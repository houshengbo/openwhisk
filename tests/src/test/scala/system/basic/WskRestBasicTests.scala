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

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.StatusCodes.BadGateway

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import spray.json.JsObject
import spray.json.JsValue
import spray.json._
import spray.json.DefaultJsonProtocol._

import common.ActivationResult
import common.rest.WskRest
import common.WskProps
import common.TestUtils.RunResult
import common.rest.RestResult

@RunWith(classOf[JUnitRunner])
class WskRestBasicTests
    extends WskBasicTests {

    override val wsk = new WskRest

    override def verifyPackage(name: String) = {
        val stdout = wsk.pkg.get(name).stdout
        val stdoutList = wsk.pkg.list().stdout
        verifyStdoutInclude(stdout, """"key":"a"""")
        verifyStdoutInclude(stdout, """"value":"A"""")
        verifyStdoutInclude(stdout, """"publish":true""")
        verifyStdoutInclude(stdout, """"version":"0.0.2"""")
        verifyStdoutInclude(stdoutList, name)
    }

    override def verifyPackageSummary(resultPackage: RunResult, ns: String, packageName: String, actionName: String) = {
        val result = resultPackage.asInstanceOf[RestResult]
        result.getField("name") shouldBe packageName
        result.getField("namespace") shouldBe ns
        val annos = result.getFieldJsValue("annotations").toString
        annos should include regex (""""value":"Package description"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
        val action = result.getFieldListJsObject("actions")(0)
        RestResult.getField(action, "name") shouldBe actionName
        val annoAction = RestResult.getFieldJsValue(action, "annotations").toString
        annoAction should include regex (""""value":"Action description"""")
        annoAction should include regex (""""name":"paramName1"""")
        annoAction should include regex (""""description":"Parameter description 2"""")
        annoAction should include regex (""""name":"paramName1"""")
        annoAction should include regex (""""description":"Parameter description 2"""")
    }

    override def verifyPackageSpace(result: RunResult, expectedString: String) = {
        result.asInstanceOf[RestResult].statusCode.intValue shouldBe OK.intValue
    }

    override def verifyPackageField(ns: String, name: String) = {
        var result = wsk.pkg.get(name)
        result.getField("namespace") shouldBe ns
        result.getField("name") shouldBe name
        result.getField("version") shouldBe "0.0.1"
        result.getFieldJsValue("publish").toString shouldBe "false"
        result.getFieldJsValue("binding").toString shouldBe "{}"
        result.getField("invalid") shouldBe ""
    }

    override def verifyRejectDuplicatePackages(stderr: String, name: String) = {
        val expectedString = "resource already exists"
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectDeletePackages(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist"
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectGetPackages(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist"
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyAction(name: String) = {
        val stdout = wsk.action.get(name).stdout
        val actionList = wsk.action.list().stdout
        stdout should not include regex(""""key":"a"""")
        stdout should not include regex(""""value":"A"""")
        stdout should include regex (""""key":"b""")
        stdout should include regex (""""value":"B"""")
        stdout should include regex (""""publish":false""")
        stdout should include regex (""""version":"0.0.2"""")
        actionList should include(name)
    }

    override def verifyRejectCreateAction(stderr: String, name: String) = {
        val expectedString = "resource already exists"
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectDeleteAction(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectInvokeAction(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectGetAction(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyCreateActionGetField(name: String, ns: String) = {
        val result = wsk.action.get(name)
        result.getField("name") shouldBe name
        result.getField("namespace") shouldBe "guest"
        result.getFieldJsValue("publish").toString shouldBe "false"
        result.getField("version") shouldBe "0.0.1"
        result.getFieldJsValue("exec").toString should include regex (""""kind":"nodejs:6","code":""")
        result.getFieldJsValue("parameters").toString should include regex (""""key":"payload","value":"test"""")
        result.getFieldJsValue("annotations").toString should include regex (""""key":"exec","value":"nodejs:6"""")
        result.getFieldJsValue("limits").toString should include regex (""""timeout":60000,"memory":256,"logs":10""")
        result.getField("invalid") shouldBe ""
    }

    override def verifyCreateInvokeBlockingActionError(input: Map[String, JsValue], name: String) = {
        val result = wsk.action.invoke(name, parameters = input, blocking = true, expectedExitCode = BadGateway.intValue)
        RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe input.toJson.asJsObject
        wsk.action.invoke(name, parameters = input, blocking = true, result = true, expectedExitCode = BadGateway.intValue).respBody shouldBe input.toJson.asJsObject
    }

    override def verifyCreateInvokeBlockingAction(name: String) = {
        val result = wsk.action.invoke(name, blocking = true, expectedExitCode = BadGateway.intValue)
        RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe JsObject("error" -> JsObject("msg" -> "failed activation on purpose".toJson))
    }

    override def verifyInvokeActionGetResult(name: String) = {
        wsk.action.invoke(name, Map("payload" -> "one two three".toJson), blocking = true, result = true, expectedExitCode = OK.intValue)
            .respData should include regex (""""count":3""")
    }

    override def verifyCreateActionSpace(res: RunResult, ns: String, name: String) = {
        val result = res.asInstanceOf[RestResult]
        result.getField("name") shouldBe name
        result.getField("namespace") shouldBe ns
        val annos = result.getFieldJsValue("annotations").toString
        annos should include regex (""""value":"Action description"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
    }

    override def verifyCreateActionSpace(result: RunResult, name: String) = {
        val res = result.asInstanceOf[RestResult]
        res.statusCode.intValue shouldBe OK.intValue
    }

    override def verifyCreateInvokeActionEmptyJson(name: String) = {
        val result = wsk.action.invoke(name, blocking = true, expectedExitCode = OK.intValue)
        RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe JsObject()
    }

    override def verifyTimeout(result: RunResult) = {
        val res = result.asInstanceOf[RestResult]
        res.getField("activationId") should not be ""
    }

    override def verifyCreateTrigger(stdout: String) = {
        verifyStdoutInclude(stdout, """"key":"a"""")
        verifyStdoutInclude(stdout, """"value":"A"""")
        verifyStdoutInclude(stdout, """"publish":false""")
        verifyStdoutInclude(stdout, """"version":"0.0.2"""")
    }

    override def verifyFireTrigger(activation: ActivationResult, dynamicParams: Map[String, JsValue]) = {
        activation.response.result shouldBe Some(dynamicParams.toJson)
        activation.duration shouldBe 0L
        activation.end shouldBe Instant.EPOCH
    }

    override def verifyFireTriggerNoParam(activation: ActivationResult) = {
        activation.response.result shouldBe Some(JsObject())
        activation.duration shouldBe 0L
        activation.end shouldBe Instant.EPOCH
    }

    override def verifyCreateGetTrigger(res: RunResult, name: String, ns: String) = {
        val result = res.asInstanceOf[RestResult]
        result.getField("name") shouldBe name
        result.getField("namespace") shouldBe ns
        val annos = result.getFieldJsValue("annotations").toString
        annos should include regex (""""value":"Trigger description"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
        annos should include regex (""""name":"paramName1"""")
        annos should include regex (""""description":"Parameter description 2"""")
    }

    override def verifyCreateTriggerGetField(name: String, ns: String) = {
        val result = wsk.trigger.get(name)
        result.getField("namespace") shouldBe ns
        result.getField("name") shouldBe name
        result.getField("version") shouldBe "0.0.1"
        result.getFieldJsValue("publish").toString shouldBe "false"
        result.getFieldJsValue("annotations").toString shouldBe "[]"
        result.getFieldJsValue("parameters").toString should include regex (""""key":"payload","value":"test"""")
        result.getFieldJsValue("limits").toString shouldBe "{}"
        result.getField("invalid") shouldBe ""

    }
    override def verifyCreateTriggerSpace(result: RunResult, name: String) = {
        result.asInstanceOf[RestResult].statusCode shouldBe OK
    }

    override def verifyRejectCreateTrigger(stderr: String, name: String) = {
        val expectedString = "resource already exists"
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectDeleteTrigger(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectGetTrigger(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectFireTrigger(stderr: String, name: String) = {
        val expectedString = "The requested resource does not exist."
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyCreateListRule(rule: RunResult, ruleList: RunResult, ruleName: String, triggerName: String, actionName: String) = {
        val stdout = rule.stdout
        stdout should include(ruleName)
        stdout should include(triggerName)
        stdout should include(actionName)
        stdout should include regex (""""version":"0.0.2"""")
        ruleList.stdout should include(ruleName)
    }

    override def verifyDisplayRuleSummary(rule: RunResult, ns: String, ruleName: String) = {
        val result = rule.asInstanceOf[RestResult]
        result.getField("name") shouldBe ruleName
        result.getField("namespace") shouldBe ns
        result.getField("status") shouldBe "active"
    }

    override def verifyCreateRuleGetField(ns: String, ruleName: String, triggerName: String, actionName: String) = {
        val rule = wsk.rule.get(ruleName)
        rule.getField("namespace") shouldBe ns
        rule.getField("name") shouldBe ruleName
        rule.getField("version") shouldBe "0.0.1"
        rule.getField("status") shouldBe "active"
        val result = wsk.rule.get(ruleName)
        val trigger = result.getFieldJsValue("trigger").toString
        trigger should include (triggerName)
        trigger should not include (actionName)
        val action = result.getFieldJsValue("action").toString
        action should not include (triggerName)
        action should include (actionName)
    }

    override def verifyRejectCreateRule(stderr: String, ruleName: String) = {
        val expectedString = s"""resource already exists"""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectDeleteRule(stderr: String, name: String) = {
        val expectedString = s"""The requested resource does not exist."""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectEnableRule(stderr: String, name: String) = {
        val expectedString = s"""The requested resource does not exist."""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectDisableRule(stderr: String, name: String) = {
        val expectedString = s"""The requested resource does not exist."""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectStatusRule(stderr: String, name: String) = {
        val expectedString = s"""The requested resource does not exist."""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def verifyRejectGetRule(stderr: String, name: String) = {
        val expectedString = s"""The requested resource does not exist."""
        verifyStdoutInclude(stderr, expectedString)
    }

    override def returnListForNS() = {
        val lines = wsk.namespace.list()
        lines.getBodyListString().size shouldBe 1
    }

    override def listEntitiesDefaultNS() = {
        val result = wsk.namespace.get(expectedExitCode = OK.intValue)(WskProps())
        result.stderr shouldBe ""
    }

    override def verifyNotListEntities(stderr: String, namespace: String) = {
        stderr should include(s"The supplied authentication is not authorized to access this resource.")
    }

    override def verifyTriggerActivation(activation: ActivationResult, ns: String, name: String) = {
        var result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("namespace"))
        result.getField("namespace") shouldBe ns
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("name"))
        result.getField("name") shouldBe name
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("version"))
        result.getField("version") shouldBe "0.0.1"
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("publish"))
        result.getFieldJsValue("publish").toString shouldBe "false"
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("subject"))
        result.getField("subject") shouldBe ns
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("activationid"))
        result.getField("activationId") shouldBe activation.activationId
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("start"))
        result.getFieldJsValue("start").toString should not be JsObject().toString
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("end"))
        result.getFieldJsValue("end").toString shouldBe JsObject().toString
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("duration"))
        result.getFieldJsValue("duration").toString shouldBe JsObject().toString
        result = wsk.activation.get(Some(activation.activationId), fieldFilter = Some("annotations"))
        result.getFieldListJsObject("annotations").length shouldBe 0
    }

    override def verifyRejectLGet(stderr: String, name: String) = {
        stderr should include regex (s"""The requested resource does not exist.""")
    }

    override def verifyRejectLog(stderr: String, name: String) = {
        stderr should include regex (s"""The requested resource does not exist.""")
    }

    override def verifyRejectResult(stderr: String, name: String) = {
        stderr should include regex (s"""The requested resource does not exist.""")
    }
}
