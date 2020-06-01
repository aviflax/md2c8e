(ns md2c8e.markdown-test
  (:require [clojure.string :as str]
            [clojure.test :refer [are deftest]]
            [md2c8e.markdown :as md]))

(deftest test-markdown->html
  (are [expected given] (= expected (#'md/markdown->html given))
    "<p><em>Very</em> <strong>simple</strong> formatting</p>\n"
    "*Very* **simple** formatting"

    "<h2 id=\"header-titles-should-become-permalinks\">Header titles should become permalinks!</h2>\n"
    "## Header titles should become permalinks!"

    "<p><del>strikethrough</del></p>\n"
    "~~strikethrough~~"
    
    "<p><a href=\"https://html5zombo.com/\">https://html5zombo.com/</a></p>\n"
    "https://html5zombo.com/"
    
    (str "<table>\n<thead>\n<tr>\n<th>Foo</th>\n<th>Bar</th>\n</tr>\n</thead>\n"
         "<tbody>\n<tr>\n<td>Baz</td>\n<td>Quux</td>\n</tr>\n</tbody>\n</table>\n")
    (str/join "\n" ["Foo | Bar"
                    "--- | ----"
                    "Baz | Quux"])
                    
    "<details><summary>Pass through</summary>\n<p>Please!</p>\n"
    "<details><summary>Pass through</summary>\n<p>Please!</p>"
    
    "<ac:random>Some “random” “custom” tags</ac:random>\n"
    "<ac:random>Some “random” “custom” tags</ac:random>"
    
    "<ac:structured-macro ac:name=\"include\"
                          ac:schema-version=\"1\"
                          ac:macro-id=\"fbad7a5b-35fa-4d13-87ed-953ffd4dc456\">
       <ac:parameter ac:name=\"\">
         <ac:link><ri:page ri:content-title=\"mt\" /></ac:link>
       </ac:parameter>
     </ac:structured-macro>"
    "<ac:structured-macro ac:name=\"include\"
                          ac:schema-version=\"1\"
                          ac:macro-id=\"fbad7a5b-35fa-4d13-87ed-953ffd4dc456\">
       <ac:parameter ac:name=\"\">
         <ac:link><ri:page ri:content-title=\"mt\" /></ac:link>
       </ac:parameter>
     </ac:structured-macro>"))
