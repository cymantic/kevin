(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.zip :as zip]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [datomic.api :as d :refer (db q)]
            [kevin.system :as sys]
            [kevin.core :refer :all]))

(defonce system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system (constantly (sys/system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system sys/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system (fn [s] (when s (sys/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))


(comment

  (reset)

  ;; number of movies, total
  (time (q '[:find (count ?e) :where [?e :movie/title]]
           (-> system :db :conn db)))

  ;; number of movies with actors
  (time
    (let [d (-> system :db :conn db)]
      (q '[:find (count ?e)
           :where
           [?e :movie/title]
           [_ :movies ?e]]
         d)))

  ;; number of movies with no actors
  (time
    (let [d (-> system :db :conn db)
          movies (q '[:find ?e :where [?e :movie/title]] d)]
      (->> (map (fn [[id]] (d/entity d id)) movies)
           (remove (fn [e] (:_movies e)))
           count)))

  ;; retract video games
  (let [d (-> system :db :conn db)]
    (->> (q '[:find ?e ?name
              :where
              [?e :movie/title ?name]] d)
         (filter (fn [[e n]] (not= -1 (.indexOf n "(VG)"))))
         (mapv (fn [[e _]] [:db.fn/retractEntity e]))
         (d/transact (-> system :db :conn))
         (deref)
         ))

  ;; zipper
  (let [d (-> system :db :conn db)
        a (actor-name->eid d "Barth, Clayton")
        actor-name (partial eid->actor-name d)
        kevin (actor-name->eid d "Bacon, Kevin (I)")
        tree (zipper d a)]
    (time (some (fn [n] (when (= kevin n) n)) tree)))


  ;; queue-based search
  (let [d (-> system :db :conn db)
        clay (actor-name->eid d "Barth, Clayton")
        kevin (actor-name->eid d "Bacon, Kevin (I)")
        neighbor-fn (partial immediate-connections d)
        actor-name (partial eid->actor-name d)]
    (time (map actor-name ((searcher clay neighbor-fn) kevin))))


  ;; query engine search (3 degrees)
  (let [d (-> system :db :conn db)
        clay (actor-name->eid d "Barth, Clayton")
        kevin (actor-name->eid d "Bacon, Kevin (I)")
        actor-name (partial eid->actor-name d)]
    (time (q '[:find ?actor ?m1 ?target
                           :in $ % ?actor ?target
                           :where (acted-with ?actor ?m1 _)
                           (acted-with ?m1 ?target _)]
                         d acted-with-rules clay kevin)))

  ;; query engine search (4 degrees)
  (let [d (-> system :db :conn db)
        clay (actor-name->eid d "Barth, Clayton")
        kevin (actor-name->eid d "Bacon, Kevin (I)")
        actor-name (partial eid->actor-name d)]
    (time (map (partial map actor-name)
               (q '[:find ?actor ?m1 ?m2 ?target
                           :in $ % ?actor ?target
                           :where (acted-with ?actor ?m1 _)
                           (acted-with ?m1 ?m2 _)
                           (acted-with ?m2 ?target _)]
                         d acted-with-rules clay kevin))))

  ;; using path from rule
  (let [d (-> system :db :conn db)
        clay (actor-name->eid d "Barth, Clayton")
        kevin (actor-name->eid d "Bacon, Kevin (I)")
        ename  (partial actor-or-movie-name d)]
    (time (map (fn [[p]] (mapv ename p))
                  (q '[:find ?path
                       :in $ % ?actor ?target
                       :where
                       (acted-with-3 ?actor ?target ?path)
                       ]
                     d acted-with-rules clay kevin))))

)