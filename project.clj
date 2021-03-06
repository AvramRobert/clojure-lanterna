(defproject clojure-lanterna "0.11.0"
  :description "A Clojure wrapper around the Lanterna terminal output library."
  :url "http://multimud.github.io/clojure-lanterna/"
  :license {:name "LGPL"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.googlecode.lanterna/lanterna "3.0.0"]]
  :java-source-paths ["./java"]
  :repositories {"releases" {:url "https://clojars.org/repo"
                             :username :env
                             :password :env
                             :sign-releases false}})
