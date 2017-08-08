/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package system.basic

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import scala.util.control.Breaks._

import common.TestUtils.RunResult
import common.rest.WskRest
import common.rest.RestResult

import spray.json.JsObject

@RunWith(classOf[JUnitRunner])
class WskRestRuleTests
    extends WskRuleTests {
    override val wsk = new WskRest

    override def verifyRuleList(ruleListResult: RunResult, ruleNameEnable: String, ruleName: String) = {
        val ruleListResultRest = ruleListResult.asInstanceOf[RestResult]
        val rules = ruleListResultRest.getBodyListJsObject()
        val ruleEnable = wsk.rule.get(ruleNameEnable)
        ruleEnable.getField("status") shouldBe "active"
        val ruleDisable = wsk.rule.get(ruleName)
        ruleDisable.getField("status") shouldBe "inactive"
        verifyListIncludeRule(rules, ruleNameEnable)
        verifyListIncludeRule(rules, ruleName)
        //listOutput.find(_.contains(ruleNameEnable)).get should (include(ruleNameEnable) and include("active"))
        //listOutput.find(_.contains(ruleName)).get should (include(ruleName) and include("inactive"))
        ruleListResultRest.respData should not include ("Unknown")
    }

    private def verifyListIncludeRule(rules: Vector[JsObject], ruleName: String) = {
        var found = false
        for (rule <- rules) {
            breakable {
                if (RestResult.getField(rule, "name") == ruleName) {
                    found = true
                    break
                }
            }
        }
        found shouldBe true
    }
}
