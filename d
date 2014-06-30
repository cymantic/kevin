#!/bin/bash

set -e

template_dir="resources/templates/"
branch="$(basename `git symbolic-ref HEAD`)"

git checkout design
middleman build
git checkout $branch

rm -Rf resources/public/*
cp -R build/* resources/public

mv resources/public/*.html $template_dir
