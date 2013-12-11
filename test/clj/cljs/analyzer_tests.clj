(ns cljs.analyzer-tests
  (:require [clojure.java.io :as io]
            [cljs.analyzer :as a]
            [cljs.env :as e])
  (:use clojure.test))

;;******************************************************************************
;;  cljs-warnings tests
;;******************************************************************************

(defn make-counter []
  (let [counter (atom 0)]
    {:counter counter
     :f (fn [& args]
          (swap! counter inc))}))

(def warning-forms
  {:undeclared-var (let [v (gensym)] `(~v 1 2 3))
   :fn-arity '(do (defn x [a b] (+ a b))
                  (x 1 2 3 4))})

(defn warn-count [form]
  (let [{:keys [counter f]} (make-counter)
        tracker (fn [warning-type env & [extra]]
                  (println "Warning: " warning-type)
                  (println "\tenabled? " (warning-type a/*cljs-warnings*)))]
    (a/with-warning-handlers [f]
      (a/analyze (a/empty-env) form))
    @counter))

(deftest no-warn
  (is (every? zero? (map (fn [[name form]] (a/no-warn (warn-count form))) warning-forms))))

(deftest all-warn
  (is (every? #(= 1 %) (map (fn [[name form]] (a/all-warn (warn-count form))) warning-forms))))

;; =============================================================================
;; Inference tests

(def test-cenv (atom {}))
(def test-env (assoc-in (a/empty-env) [:ns :name] 'cljs.core))

(e/with-compiler-env test-cenv
  (a/analyze-file (io/file "src/cljs/cljs/core.cljs")))

(deftest basic-inference
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '1)))
         'number))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '"foo")))
         'string))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(make-array 10))))
         'array))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(js-obj))))
         'object))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '[])))
         'cljs.core/IVector))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '{})))
         'cljs.core/IMap))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '#{})))
         'cljs.core/ISet))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env ())))
         'cljs.core/IList))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(fn [x] x))))
         'function)))

(deftest if-inference
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(if x "foo" 1))))
         '#{number string})))

(deftest fn-inference
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env
                   '(let [x (fn ([a] 1) ([a b] "foo") ([a b & r] ()))]
                      (x :one)))))
        'number))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env
                   '(let [x (fn ([a] 1) ([a b] "foo") ([a b & r] ()))]
                      (x :one :two)))))
        'string))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env
                   '(let [x (fn ([a] 1) ([a b] "foo") ([a b & r] ()))]
                      (x :one :two :three)))))
        'cljs.core/IList)))

(deftest lib-inference
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(+ 1 2))))
         'number))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(alength (array)))))
         'number))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(aclone (array)))))
         'array))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(count [1 2 3]))))
         'number))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(into-array [1 2 3]))))
         'array))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(js-obj))))
         'object))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(-conj [] 1))))
         'clj))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(conj [] 1))))
         'clj))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(assoc nil :foo :bar))))
         'clj))
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(dissoc {:foo :bar} :foo))))
         'clj)))

(deftest test-always-true-if
  (is (= (e/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(if 1 2 "foo"))))
         'number)))

;; will only work if the previous test works
(deftest test-count
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(count []))))
         'number)))

(deftest test-numeric
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(dec x))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(int x))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(unchecked-int x))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(mod x y))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(quot x y))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(rem x y))))
         'number))
  (is (= (cljs.env/with-compiler-env test-cenv
           (:tag (a/analyze test-env '(bit-count n))))
         'number)))
