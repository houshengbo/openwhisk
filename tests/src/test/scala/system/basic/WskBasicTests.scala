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

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import common.ActivationResult
import common.TestHelpers
import common.TestUtils
import common.TestUtils._
import common.BaseWsk
import common.Wsk
import common.WskProps
import common.WskTestHelpers
import spray.json._
import spray.json.DefaultJsonProtocol._
//import spray.json.pimpAny

@RunWith(classOf[JUnitRunner])
<<<<<<< HEAD
class WskBasicTests extends TestHelpers with WskTestHelpers {

  implicit val wskprops = WskProps()
  val wsk = new Wsk
  val defaultAction = Some(TestUtils.getTestActionFilename("hello.js"))

  behavior of "Wsk CLI"

  it should "reject creating duplicate entity" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "testDuplicateCreate"
    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name)
    }
    assetHelper.withCleaner(wsk.action, name, confirmDelete = false) { (action, _) =>
      action.create(name, defaultAction, expectedExitCode = CONFLICT)
    }
  }

  it should "reject deleting entity in wrong collection" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "testCrossDelete"
    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name)
    }
    wsk.action.delete(name, expectedExitCode = CONFLICT)
  }

  it should "reject unauthenticated access" in {
    implicit val wskprops = WskProps("xxx") // shadow properties
    val errormsg = "The supplied authentication is invalid"
    wsk.namespace.list(expectedExitCode = UNAUTHORIZED).stderr should include(errormsg)
    wsk.namespace.get(expectedExitCode = UNAUTHORIZED).stderr should include(errormsg)
  }

  behavior of "Wsk Package CLI"

  it should "create, update, get and list a package" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "testPackage"
    val params = Map("a" -> "A".toJson)
    assetHelper.withCleaner(wsk.pkg, name) { (pkg, _) =>
      pkg.create(name, parameters = params, shared = Some(true))
      pkg.create(name, update = true)
    }
    val stdout = wsk.pkg.get(name).stdout
    stdout should include regex (""""key": "a"""")
    stdout should include regex (""""value": "A"""")
    stdout should include regex (""""publish": true""")
    stdout should include regex (""""version": "0.0.2"""")
    wsk.pkg.list().stdout should include(name)
  }

  it should "create, and get a package summary" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val packageName = "packageName"
    val actionName = "actionName"
    val packageAnnots = Map(
      "description" -> JsString("Package description"),
      "parameters" -> JsArray(
        JsObject("name" -> JsString("paramName1"), "description" -> JsString("Parameter description 1")),
        JsObject("name" -> JsString("paramName2"), "description" -> JsString("Parameter description 2"))))
    val actionAnnots = Map(
      "description" -> JsString("Action description"),
      "parameters" -> JsArray(
        JsObject("name" -> JsString("paramName1"), "description" -> JsString("Parameter description 1")),
        JsObject("name" -> JsString("paramName2"), "description" -> JsString("Parameter description 2"))))

    assetHelper.withCleaner(wsk.pkg, packageName) { (pkg, _) =>
      pkg.create(packageName, annotations = packageAnnots)
    }

    wsk.action.create(packageName + "/" + actionName, defaultAction, annotations = actionAnnots)
    val stdout = wsk.pkg.get(packageName, summary = true).stdout
    val ns = wsk.namespace.whois()
    wsk.action.delete(packageName + "/" + actionName)

    stdout should include regex (s"(?i)package /$ns/$packageName: Package description\\s*\\(parameters: paramName1, paramName2\\)")
    stdout should include regex (s"(?i)action /$ns/$packageName/$actionName: Action description\\s*\\(parameters: paramName1, paramName2\\)")
  }

  it should "create a package with a name that contains spaces" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "package with spaces"

    val res = assetHelper.withCleaner(wsk.pkg, name) { (pkg, _) =>
      pkg.create(name)
    }

    res.stdout should include(s"ok: created package $name")
  }

  it should "create a package, and get its individual fields" in withAssetCleaner(wskprops) {
    val name = "packageFields"
    val paramInput = Map("payload" -> "test".toJson)
    val successMsg = s"ok: got package $name, displaying field"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.pkg, name) { (pkg, _) =>
        pkg.create(name, parameters = paramInput)
      }

      val expectedParam = JsObject("payload" -> JsString("test"))
      val ns = wsk.namespace.whois()

      wsk.pkg
        .get(name, fieldFilter = Some("namespace"))
        .stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
      wsk.pkg.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
      wsk.pkg.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
      wsk.pkg.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
      wsk.pkg.get(name, fieldFilter = Some("binding")).stdout should include regex (s"""\\{\\}""")
      wsk.pkg.get(name, fieldFilter = Some("invalid"), expectedExitCode = ERROR_EXIT).stderr should include(
        "error: Invalid field filter 'invalid'.")
  }

  it should "reject creation of duplication packages" in withAssetCleaner(wskprops) {
    val name = "dupePackage"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.pkg, name) { (pkg, _) =>
        pkg.create(name)
      }

      val stderr = wsk.pkg.create(name, expectedExitCode = CONFLICT).stderr
      stderr should include regex (s"""Unable to create package '$name': resource already exists \\(code \\d+\\)""")
  }

  it should "reject delete of package that does not exist" in {
    val name = "nonexistentPackage"
    val stderr = wsk.pkg.delete(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to delete package '$name'. The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject get of package that does not exist" in {
    val name = "nonexistentPackage"
    val stderr = wsk.pkg.get(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get package '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  behavior of "Wsk Action CLI"

  it should "create the same action twice with different cases" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    assetHelper.withCleaner(wsk.action, "TWICE") { (action, name) =>
      action.create(name, defaultAction)
    }
    assetHelper.withCleaner(wsk.action, "twice") { (action, name) =>
      action.create(name, defaultAction)
    }
  }

  it should "create, update, get and list an action" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "createAndUpdate"
    val file = Some(TestUtils.getTestActionFilename("hello.js"))
    val params = Map("a" -> "A".toJson)
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, file, parameters = params)
      action.create(name, None, parameters = Map("b" -> "B".toJson), update = true)
    }

    val stdout = wsk.action.get(name).stdout
    stdout should not include regex(""""key": "a"""")
    stdout should not include regex(""""value": "A"""")
    stdout should include regex (""""key": "b""")
    stdout should include regex (""""value": "B"""")
    stdout should include regex (""""publish": false""")
    stdout should include regex (""""version": "0.0.2"""")
    wsk.action.list().stdout should include(name)
  }

  it should "reject create of an action that already exists" in withAssetCleaner(wskprops) {
    val name = "dupeAction"
    val file = Some(TestUtils.getTestActionFilename("echo.js"))

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file)
      }

      val stderr = wsk.action.create(name, file, expectedExitCode = CONFLICT).stderr
      stderr should include regex (s"""Unable to create action '$name': resource already exists \\(code \\d+\\)""")
  }

  it should "reject delete of action that does not exist" in {
    val name = "nonexistentAction"
    val stderr = wsk.action.delete(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to delete action '$name'. The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject invocation of action that does not exist" in {
    val name = "nonexistentAction"
    val stderr = wsk.action.invoke(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to invoke action '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject get of an action that does not exist" in {
    val name = "nonexistentAction"
    val stderr = wsk.action.get(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get action '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "create, and invoke an action that utilizes a docker container" in withAssetCleaner(wskprops) {
    val name = "dockerContainer"
    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) {
        // this docker image will be need to be pulled from dockerhub and hence has to be published there first
        (action, _) =>
          action.create(name, None, docker = Some("openwhisk/example"))
      }

      val args = Map("payload" -> "test".toJson)
      val run = wsk.action.invoke(name, args)
      withActivation(wsk.activation, run) { activation =>
        activation.response.result shouldBe Some(
          JsObject("args" -> args.toJson, "msg" -> "Hello from arbitrary C program!".toJson))
      }
  }

  it should "create, and invoke an action that utilizes dockerskeleton with native zip" in withAssetCleaner(wskprops) {
    val name = "dockerContainerWithZip"
    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) {
        // this docker image will be need to be pulled from dockerhub and hence has to be published there first
        (action, _) =>
          action.create(name, Some(TestUtils.getTestActionFilename("blackbox.zip")), kind = Some("native"))
      }

      val run = wsk.action.invoke(name, Map())
      withActivation(wsk.activation, run) { activation =>
        activation.response.result shouldBe Some(JsObject("msg" -> "hello zip".toJson))
        activation.logs shouldBe defined
        val logs = activation.logs.get.toString
        logs should include("This is an example zip used with the docker skeleton action.")
        logs should not include ("XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX")
      }
  }

  it should "create, and invoke an action using a parameter file" in withAssetCleaner(wskprops) {
    val name = "paramFileAction"
    val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
    val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, file)
      }

      val expectedOutput = JsObject("payload" -> JsString("test"))
      val run = wsk.action.invoke(name, parameterFile = argInput)
      withActivation(wsk.activation, run) { activation =>
        activation.response.result shouldBe Some(expectedOutput)
      }
  }

  it should "create an action, and get its individual fields" in withAssetCleaner(wskprops) {
    val name = "actionFields"
    val paramInput = Map("payload" -> "test".toJson)
    val successMsg = s"ok: got action $name, displaying field"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, defaultAction, parameters = paramInput)
      }

      val expectedParam = JsObject("payload" -> JsString("test"))
      val ns = wsk.namespace.whois()

      wsk.action.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
      wsk.action.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
      wsk.action.get(name, fieldFilter = Some("exec")).stdout should include(s"""$successMsg""")
      wsk.action
        .get(name, fieldFilter = Some("exec"))
        .stdout should include regex (s"""$successMsg exec\n\\{\\s+"kind":\\s+"nodejs:6",\\s+"code":\\s+"\\/\\*\\*[\\\\r]*\\\\n \\* Hello, world.[\\\\r]*\\\\n \\*\\/[\\\\r]*\\\\nfunction main\\(params\\) \\{[\\\\r]*\\\\n    greeting \\= 'hello, ' \\+ params.payload \\+ '!'[\\\\r]*\\\\n    console.log\\(greeting\\);[\\\\r]*\\\\n    return \\{payload: greeting\\}[\\\\r]*\\\\n\\}""")
      wsk.action
        .get(name, fieldFilter = Some("parameters"))
        .stdout should include regex (s"""$successMsg parameters\n\\[\\s+\\{\\s+"key":\\s+"payload",\\s+"value":\\s+"test"\\s+\\}\\s+\\]""")
      wsk.action
        .get(name, fieldFilter = Some("annotations"))
        .stdout should include regex (s"""$successMsg annotations\n\\[\\s+\\{\\s+"key":\\s+"exec",\\s+"value":\\s+"nodejs:6"\\s+\\}\\s+\\]""")
      wsk.action
        .get(name, fieldFilter = Some("limits"))
        .stdout should include regex (s"""$successMsg limits\n\\{\\s+"timeout":\\s+60000,\\s+"memory":\\s+256,\\s+"logs":\\s+10\\s+\\}""")
      wsk.action
        .get(name, fieldFilter = Some("namespace"))
        .stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
      wsk.action.get(name, fieldFilter = Some("invalid"), expectedExitCode = MISUSE_EXIT).stderr should include(
        "error: Invalid field filter 'invalid'.")
      wsk.action.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
  }

  /**
   * Tests creating an action from a malformed js file. This should fail in
   * some way - preferably when trying to create the action. If not, then
   * surely when it runs there should be some indication in the logs. Don't
   * think this is true currently.
   */
  it should "create and invoke action with malformed js resulting in activation error" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val name = "MALFORMED"
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, Some(TestUtils.getTestActionFilename("malformed.js")))
      }

      val run = wsk.action.invoke(name, Map("payload" -> "whatever".toJson))
      withActivation(wsk.activation, run) { activation =>
        activation.response.status shouldBe "action developer error"
        // representing nodejs giving an error when given malformed.js
        activation.response.result.get.toString should include("ReferenceError")
      }
  }

  it should "create and invoke a blocking action resulting in an application error response" in withAssetCleaner(
    wskprops) { (wp, assetHelper) =>
    val name = "applicationError"
    val strErrInput = Map("error" -> "Error message".toJson)
    val numErrInput = Map("error" -> 502.toJson)
    val boolErrInput = Map("error" -> true.toJson)

    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, Some(TestUtils.getTestActionFilename("echo.js")))
    }

    Seq(strErrInput, numErrInput, boolErrInput) foreach { input =>
      getJSONFromCLIResponse(
        wsk.action.invoke(name, parameters = input, blocking = true, expectedExitCode = 246).stderr)
        .fields("response")
        .asJsObject
        .fields("result")
        .asJsObject shouldBe input.toJson.asJsObject

      wsk.action
        .invoke(name, parameters = input, blocking = true, result = true, expectedExitCode = 246)
        .stderr
        .parseJson
        .asJsObject shouldBe input.toJson.asJsObject
    }
  }

  it should "create and invoke a blocking action resulting in an failed promise" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val name = "errorResponseObject"
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, Some(TestUtils.getTestActionFilename("asyncError.js")))
      }

      val stderr = wsk.action.invoke(name, blocking = true, expectedExitCode = 246).stderr
      ActivationResult.serdes.read(removeCLIHeader(stderr).parseJson).response.result shouldBe Some {
        JsObject("error" -> JsObject("msg" -> "failed activation on purpose".toJson))
      }
  }

  it should "invoke a blocking action and get only the result" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "basicInvoke"
    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, Some(TestUtils.getTestActionFilename("wc.js")))
    }

    wsk.action
      .invoke(name, Map("payload" -> "one two three".toJson), result = true)
      .stdout should include regex (""""count": 3""")
  }

  it should "create, and get an action summary" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "actionName"
    val annots = Map(
      "description" -> JsString("Action description"),
      "parameters" -> JsArray(
        JsObject("name" -> JsString("paramName1"), "description" -> JsString("Parameter description 1")),
        JsObject("name" -> JsString("paramName2"), "description" -> JsString("Parameter description 2"))))

    assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, defaultAction, annotations = annots)
    }

    val stdout = wsk.action.get(name, summary = true).stdout
    val ns = wsk.namespace.whois()

    stdout should include regex (s"(?i)action /$ns/$name: Action description\\s*\\(parameters: paramName1, paramName2\\)")
  }

  it should "create an action with a name that contains spaces" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "action with spaces"

    val res = assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, defaultAction)
    }

    res.stdout should include(s"ok: created action $name")
  }

  it should "create an action, and invoke an action that returns an empty JSON object" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val name = "emptyJSONAction"

      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, Some(TestUtils.getTestActionFilename("emptyJSONResult.js")))
      }

      val stdout = wsk.action.invoke(name, result = true).stdout
      stdout.parseJson.asJsObject shouldBe JsObject()
  }

  it should "create, and invoke an action that times out to ensure the proper response is received" in withAssetCleaner(
    wskprops) { (wp, assetHelper) =>
    val name = "sleepAction"
    val params = Map("payload" -> "100000".toJson)
    val allowedActionDuration = 120 seconds
    val res = assetHelper.withCleaner(wsk.action, name) { (action, _) =>
      action.create(name, Some(TestUtils.getTestActionFilename("timeout.js")), timeout = Some(allowedActionDuration))
      action.invoke(name, parameters = params, result = true, expectedExitCode = ACCEPTED)
=======
abstract class WskBasicTests
    extends TestHelpers
    with WskTestHelpers {

    implicit val wskprops = WskProps()
    val wsk: BaseWsk
    val defaultAction = Some(TestUtils.getTestActionFilename("hello.js"))

    behavior of "Wsk CLI"

    it should "reject creating duplicate entity" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testDuplicateCreate"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, name, confirmDelete = false) {
                (action, _) => action.create(name, defaultAction, expectedExitCode = CONFLICT)
            }
>>>>>>> 7e0c0e9... Replace the test cases with REST implementation
    }

    res.stderr should include("""but the request has not yet finished""")
  }

  it should "create, and get docker action get ensure exec code is omitted" in withAssetCleaner(wskprops) {
    val name = "dockerContainer"
    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.action, name) { (action, _) =>
        action.create(name, None, docker = Some("fake-container"))
      }

      wsk.action.get(name).stdout should not include (""""code"""")
  }

  behavior of "Wsk Trigger CLI"

  it should "create, update, get, fire and list trigger" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "listTriggers"
    val params = Map("a" -> "A".toJson)
    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name, parameters = params)
      trigger.create(name, update = true)
    }
    val stdout = wsk.trigger.get(name).stdout
    stdout should include regex (""""key": "a"""")
    stdout should include regex (""""value": "A"""")
    stdout should include regex (""""publish": false""")
    stdout should include regex (""""version": "0.0.2"""")

    val dynamicParams = Map("t" -> "T".toJson)
    val run = wsk.trigger.fire(name, dynamicParams)
    withActivation(wsk.activation, run) { activation =>
      activation.response.result shouldBe Some(dynamicParams.toJson)
      activation.duration shouldBe 0L // shouldn't exist but CLI generates it
      activation.end shouldBe Instant.EPOCH // shouldn't exist but CLI generates it
    }

    val runWithNoParams = wsk.trigger.fire(name, Map())
    withActivation(wsk.activation, runWithNoParams) { activation =>
      activation.response.result shouldBe Some(JsObject())
      activation.duration shouldBe 0L // shouldn't exist but CLI generates it
      activation.end shouldBe Instant.EPOCH // shouldn't exist but CLI generates it
    }

<<<<<<< HEAD
    wsk.trigger.list().stdout should include(name)
  }

  it should "create, and get a trigger summary" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "triggerName"
    val annots = Map(
      "description" -> JsString("Trigger description"),
      "parameters" -> JsArray(
        JsObject("name" -> JsString("paramName1"), "description" -> JsString("Parameter description 1")),
        JsObject("name" -> JsString("paramName2"), "description" -> JsString("Parameter description 2"))))

    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name, annotations = annots)
    }

    val stdout = wsk.trigger.get(name, summary = true).stdout
    val ns = wsk.namespace.whois()

    stdout should include regex (s"trigger /$ns/$name: Trigger description\\s*\\(parameters: paramName1, paramName2\\)")
  }

  it should "create a trigger with a name that contains spaces" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "trigger with spaces"

    val res = assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name)
    }

    res.stdout should include regex (s"ok: created trigger $name")
  }

  it should "create, and fire a trigger using a parameter file" in withAssetCleaner(wskprops) {
    val name = "paramFileTrigger"
    val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
    val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
        trigger.create(name)
      }

      val expectedOutput = JsObject("payload" -> JsString("test"))
      val run = wsk.trigger.fire(name, parameterFile = argInput)
      withActivation(wsk.activation, run) { activation =>
        activation.response.result shouldBe Some(expectedOutput)
      }
  }

  it should "create a trigger, and get its individual fields" in withAssetCleaner(wskprops) {
    val name = "triggerFields"
    val paramInput = Map("payload" -> "test".toJson)
    val successMsg = s"ok: got trigger $name, displaying field"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
        trigger.create(name, parameters = paramInput)
      }

      val expectedParam = JsObject("payload" -> JsString("test"))
      val ns = wsk.namespace.whois()

      wsk.trigger
        .get(name, fieldFilter = Some("namespace"))
        .stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
      wsk.trigger.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
      wsk.trigger.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
      wsk.trigger.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
      wsk.trigger.get(name, fieldFilter = Some("annotations")).stdout should include(s"""$successMsg annotations\n[]""")
      wsk.trigger
        .get(name, fieldFilter = Some("parameters"))
        .stdout should include regex (s"""$successMsg parameters\n\\[\\s+\\{\\s+"key":\\s+"payload",\\s+"value":\\s+"test"\\s+\\}\\s+\\]""")
      wsk.trigger.get(name, fieldFilter = Some("limits")).stdout should include(s"""$successMsg limits\n{}""")
      wsk.trigger.get(name, fieldFilter = Some("invalid"), expectedExitCode = ERROR_EXIT).stderr should include(
        "error: Invalid field filter 'invalid'.")
  }

  it should "create, and fire a trigger to ensure result is empty" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val name = "emptyResultTrigger"
    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name)
    }

    val run = wsk.trigger.fire(name)
    withActivation(wsk.activation, run) { activation =>
      activation.response.result shouldBe Some(JsObject())
    }
  }

  it should "reject creation of duplicate triggers" in withAssetCleaner(wskprops) {
    val name = "dupeTrigger"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
        trigger.create(name)
      }

      val stderr = wsk.trigger.create(name, expectedExitCode = CONFLICT).stderr
      stderr should include regex (s"""Unable to create trigger '$name': resource already exists \\(code \\d+\\)""")
  }

  it should "reject delete of trigger that does not exist" in {
    val name = "nonexistentTrigger"
    val stderr = wsk.trigger.delete(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get trigger '$name'. The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject get of trigger that does not exist" in {
    val name = "nonexistentTrigger"
    val stderr = wsk.trigger.get(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get trigger '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject firing of a trigger that does not exist" in {
    val name = "nonexistentTrigger"
    val stderr = wsk.trigger.fire(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to fire trigger '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  behavior of "Wsk Rule CLI"

  it should "create rule, get rule, update rule and list rule" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
    val ruleName = "listRules"
    val triggerName = "listRulesTrigger"
    val actionName = "listRulesAction"

    assetHelper.withCleaner(wsk.trigger, triggerName) { (trigger, name) =>
      trigger.create(name)
    }
    assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
      action.create(name, defaultAction)
    }
    assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
      rule.create(name, trigger = triggerName, action = actionName)
    }

    // finally, we perform the update, and expect success this time
    wsk.rule.create(ruleName, trigger = triggerName, action = actionName, update = true)

    val stdout = wsk.rule.get(ruleName).stdout
    stdout should include(ruleName)
    stdout should include(triggerName)
    stdout should include(actionName)
    stdout should include regex (""""version": "0.0.2"""")
    wsk.rule.list().stdout should include(ruleName)
  }

  it should "create rule, get rule, ensure rule is enabled by default" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val ruleName = "enabledRule"
      val triggerName = "enabledRuleTrigger"
      val actionName = "enabledRuleAction"

      assetHelper.withCleaner(wsk.trigger, triggerName) { (trigger, name) =>
        trigger.create(name)
      }
      assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
        action.create(name, defaultAction)
      }
      assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
        rule.create(name, trigger = triggerName, action = actionName)
      }

      val stdout = wsk.rule.get(ruleName).stdout
      stdout should include regex (""""status":\s*"active"""")
  }

  it should "display a rule summary when --summary flag is used with 'wsk rule get'" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val ruleName = "mySummaryRule"
      val triggerName = "summaryRuleTrigger"
      val actionName = "summaryRuleAction"

      assetHelper.withCleaner(wsk.trigger, triggerName) { (trigger, name) =>
        trigger.create(name)
      }
      assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
        action.create(name, defaultAction)
      }
      assetHelper.withCleaner(wsk.rule, ruleName, confirmDelete = false) { (rule, name) =>
        rule.create(name, trigger = triggerName, action = actionName)
      }

      // Summary namespace should match one of the allowable namespaces (typically 'guest')
      val ns = wsk.namespace.whois()
      val stdout = wsk.rule.get(ruleName, summary = true).stdout

      stdout should include regex (s"(?i)rule /$ns/$ruleName\\s*\\(status: active\\)")
  }

  it should "create a rule, and get its individual fields" in withAssetCleaner(wskprops) {
    val ruleName = "ruleFields"
    val triggerName = "ruleTriggerFields"
    val actionName = "ruleActionFields"
    val paramInput = Map("payload" -> "test".toJson)
    val successMsg = s"ok: got rule $ruleName, displaying field"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.trigger, triggerName) { (trigger, name) =>
        trigger.create(name)
      }
      assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
        action.create(name, defaultAction)
      }
      assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
        rule.create(name, trigger = triggerName, action = actionName)
      }

      val ns = wsk.namespace.whois()
      wsk.rule
        .get(ruleName, fieldFilter = Some("namespace"))
        .stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
      wsk.rule.get(ruleName, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$ruleName"""")
      wsk.rule.get(ruleName, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"\n""")
      wsk.rule.get(ruleName, fieldFilter = Some("status")).stdout should include(s"""$successMsg status\n"active"""")
      val trigger = wsk.rule.get(ruleName, fieldFilter = Some("trigger")).stdout
      trigger should include regex (s"""$successMsg trigger\n""")
      trigger should include(triggerName)
      trigger should not include (actionName)
      val action = wsk.rule.get(ruleName, fieldFilter = Some("action")).stdout
      action should include regex (s"""$successMsg action\n""")
      action should include(actionName)
      action should not include (triggerName)
  }

  it should "reject creation of duplicate rules" in withAssetCleaner(wskprops) {
    val ruleName = "dupeRule"
    val triggerName = "triggerName"
    val actionName = "actionName"

    (wp, assetHelper) =>
      assetHelper.withCleaner(wsk.trigger, triggerName) { (trigger, name) =>
        trigger.create(name)
      }
      assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
        action.create(name, defaultAction)
      }
      assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
        rule.create(name, trigger = triggerName, action = actionName)
      }

      val stderr =
        wsk.rule.create(ruleName, trigger = triggerName, action = actionName, expectedExitCode = CONFLICT).stderr
      stderr should include regex (s"""Unable to create rule '$ruleName': resource already exists \\(code \\d+\\)""")
  }

  it should "reject delete of rule that does not exist" in {
    val name = "nonexistentRule"
    val stderr = wsk.rule.delete(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to delete rule '$name'. The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject enable of rule that does not exist" in {
    val name = "nonexistentRule"
    val stderr = wsk.rule.enable(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to enable rule '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject disable of rule that does not exist" in {
    val name = "nonexistentRule"
    val stderr = wsk.rule.disable(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to disable rule '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject status of rule that does not exist" in {
    val name = "nonexistentRule"
    val stderr = wsk.rule.state(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get status of rule '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject get of rule that does not exist" in {
    val name = "nonexistentRule"
    val stderr = wsk.rule.get(name, expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get rule '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  behavior of "Wsk Namespace CLI"

  it should "return a list of exactly one namespace" in {
    val lines = wsk.namespace.list().stdout.lines.toSeq
    lines should have size 2
    lines.head shouldBe "namespaces"
    lines(1).trim should not be empty
  }

  it should "list entities in default namespace" in {
    // use a fresh wsk props instance that is guaranteed to use
    // the default namespace
    wsk.namespace.get(expectedExitCode = SUCCESS_EXIT)(WskProps()).stdout should include("default")
  }

  it should "not list entities with an invalid namespace" in {
    val namespace = "fakeNamespace"
    val stderr = wsk.namespace.get(Some(s"/${namespace}"), expectedExitCode = FORBIDDEN).stderr

    stderr should include(s"Unable to obtain the list of entities for namespace '${namespace}'")
  }

  behavior of "Wsk Activation CLI"

  it should "create a trigger, and fire a trigger to get its individual fields from an activation" in withAssetCleaner(
    wskprops) { (wp, assetHelper) =>
    val name = "activationFields"

    assetHelper.withCleaner(wsk.trigger, name) { (trigger, _) =>
      trigger.create(name)
    }

    val ns = s""""${wsk.namespace.whois()}""""
    val run = wsk.trigger.fire(name)
    withActivation(wsk.activation, run) { activation =>
      val successMsg = s"ok: got activation ${activation.activationId}, displaying field"
      wsk.activation
        .get(Some(activation.activationId), fieldFilter = Some("namespace"))
        .stdout should include regex (s"""(?i)$successMsg namespace\n$ns""")
      wsk.activation.get(Some(activation.activationId), fieldFilter = Some("name")).stdout should include(
        s"""$successMsg name\n"$name"""")
      wsk.activation.get(Some(activation.activationId), fieldFilter = Some("version")).stdout should include(
        s"""$successMsg version\n"0.0.1"""")
      wsk.activation.get(Some(activation.activationId), fieldFilter = Some("publish")).stdout should include(
        s"""$successMsg publish\nfalse""")
      wsk.activation
        .get(Some(activation.activationId), fieldFilter = Some("subject"))
        .stdout should include regex (s"""(?i)$successMsg subject\n""")
      wsk.activation.get(Some(activation.activationId), fieldFilter = Some("activationid")).stdout should include(
        s"""$successMsg activationid\n"${activation.activationId}""")
      wsk.activation
        .get(Some(activation.activationId), fieldFilter = Some("start"))
        .stdout should include regex (s"""$successMsg start\n\\d""")
      wsk.activation
        .get(Some(activation.activationId), fieldFilter = Some("end"))
        .stdout should include regex (s"""$successMsg end\n\\d""")
      wsk.activation
        .get(Some(activation.activationId), fieldFilter = Some("duration"))
        .stdout should include regex (s"""$successMsg duration\n\\d""")
      wsk.activation.get(Some(activation.activationId), fieldFilter = Some("annotations")).stdout should include(
        s"""$successMsg annotations\n[]""")
    }
  }

  it should "reject get of activation that does not exist" in {
    val name = "0" * 32
    val stderr = wsk.activation.get(Some(name), expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject logs of activation that does not exist" in {
    val name = "0" * 32
    val stderr = wsk.activation.logs(Some(name), expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get logs for activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject result of activation that does not exist" in {
    val name = "0" * 32
    val stderr = wsk.activation.result(Some(name), expectedExitCode = NOT_FOUND).stderr
    stderr should include regex (s"""Unable to get result for activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
  }

  it should "reject activation request when using activation ID with --last Flag" in withAssetCleaner(wskprops) {
    (wp, assetHelper) =>
      val auth: Seq[String] = Seq("--auth", wskprops.authKey)

      val lastId = "dummyActivationId"
      val tooManyArgsMsg = s"${lastId}. An activation ID is required."
      val invalidField = s"Invalid field filter '${lastId}'."

      val invalidCmd = Seq(
        (Seq("activation", "get", s"$lastId", "publish", "--last"), tooManyArgsMsg),
        (Seq("activation", "get", s"$lastId", "--last"), invalidField),
        (Seq("activation", "logs", s"$lastId", "--last"), tooManyArgsMsg),
        (Seq("activation", "result", s"$lastId", "--last"), tooManyArgsMsg))

      invalidCmd foreach {
        case (cmd, err) =>
          val stderr = wsk.cli(cmd ++ wskprops.overrides ++ auth, expectedExitCode = ERROR_EXIT).stderr
          stderr should include(err)
      }
  }
=======
    behavior of "Wsk Package CLI"

    it should "create, update, get and list a package" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testPackage"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) =>
                    pkg.create(name, parameters = params, shared = Some(true))
                    pkg.create(name, update = true)
            }
            verifyPackage(name)
    }

    def verifyPackage(name: String) = {
        val stdout = wsk.pkg.get(name).stdout
        val stdoutList = wsk.pkg.list().stdout
        verifyStdoutInclude(stdout, """"key": "a"""")
        verifyStdoutInclude(stdout, """"value": "A"""")
        verifyStdoutInclude(stdout, """"publish": true""")
        verifyStdoutInclude(stdout, """"version": "0.0.2"""")
        verifyStdoutInclude(stdoutList, name)
    }

    def verifyStdoutInclude(stdout: String, expectedInclude: String) = {
        stdout should include regex expectedInclude
    }

    it should "create, and get a package summary" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val packageName = "packageName"
            val actionName = "actionName"
            val packageAnnots = Map(
                "description" -> JsString("Package description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))
            val actionAnnots = Map(
                "description" -> JsString("Action description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, _) => pkg.create(packageName, annotations = packageAnnots)
            }

            wsk.action.create(packageName + "/" + actionName, defaultAction, annotations = actionAnnots)
            val result = wsk.pkg.get(packageName, summary = true)
            val ns = wsk.namespace.whois()
            wsk.action.delete(packageName + "/" + actionName)

            verifyPackageSummary(result, ns, packageName, actionName)
    }

    def verifyPackageSummary(result: RunResult, ns: String, packageName: String, actionName: String) = {
        result.stdout should include regex (s"(?i)package /$ns/$packageName: Package description\\s*\\(parameters: paramName1, paramName2\\)")
        result.stdout should include regex (s"(?i)action /$ns/$packageName/$actionName: Action description\\s*\\(parameters: paramName1, paramName2\\)")

    }

    it should "create a package with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "package with spaces"

            val res = assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) => pkg.create(name)
            }

            verifyPackageSpace(res, s"ok: created package $name")
    }

    def verifyPackageSpace(result: RunResult, expectedString: String) = {
        verifyStdoutInclude(result.stdout, expectedString)
    }

    it should "create a package, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "packageFields"
        val paramInput = Map("payload" -> "test".toJson)

        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) => pkg.create(name, parameters = paramInput)
            }

            val expectedParam = JsObject("payload" -> JsString("test"))
            val ns = wsk.namespace.whois()

            verifyPackageField(ns, name)
    }

    def verifyPackageField(ns: String, name: String) = {
        val successMsg = s"ok: got package $name, displaying field"
        wsk.pkg.get(name, fieldFilter = Some("namespace")).stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
        wsk.pkg.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
        wsk.pkg.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
        wsk.pkg.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
        wsk.pkg.get(name, fieldFilter = Some("binding")).stdout should include regex (s"""\\{\\}""")
        wsk.pkg.get(name, fieldFilter = Some("invalid"), expectedExitCode = ERROR_EXIT).stderr should include("error: Invalid field filter 'invalid'.")
    }

    it should "reject creation of duplication packages" in withAssetCleaner(wskprops) {
        val name = "dupePackage"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.pkg, name) {
            (pkg, _) => pkg.create(name)
        }

        val stderr = wsk.pkg.create(name, expectedExitCode = CONFLICT).stderr
        verifyRejectDuplicatePackages(stderr, name)
    }

    def verifyRejectDuplicatePackages(stderr: String, name: String) = {
        val expectedString = s"""Unable to create package '$name': resource already exists \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "reject delete of package that does not exist" in {
        val name = "nonexistentPackage"
        val stderr = wsk.pkg.delete(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectDeletePackages(stderr, name)
    }

    def verifyRejectDeletePackages(stderr: String, name: String) = {
        val expectedString = s"""Unable to delete package '$name'. The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "reject get of package that does not exist" in {
        val name = "nonexistentPackage"
        val stderr = wsk.pkg.get(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectGetPackages(stderr, name)
    }

    def verifyRejectGetPackages(stderr: String, name: String) = {
        val expectedString = s"""Unable to get package '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    behavior of "Wsk Action CLI"

    it should "create the same action twice with different cases" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.action, "TWICE") { (action, name) => action.create(name, defaultAction) }
            assetHelper.withCleaner(wsk.action, "twice") { (action, name) => action.create(name, defaultAction) }
    }

    it should "create, update, get and list an action" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "createAndUpdate"
            val file = Some(TestUtils.getTestActionFilename("hello.js"))
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, file, parameters = params)
                    action.create(name, None, parameters = Map("b" -> "B".toJson), update = true)
            }

            verifyAction(name)

    }

    def verifyAction(name: String) = {
        val stdout = wsk.action.get(name).stdout
        val actionList = wsk.action.list().stdout
        stdout should not include regex(""""key": "a"""")
        stdout should not include regex(""""value": "A"""")
        stdout should include regex (""""key": "b""")
        stdout should include regex (""""value": "B"""")
        stdout should include regex (""""publish": false""")
        stdout should include regex (""""version": "0.0.2"""")
        actionList should include(name)
    }

    it should "reject create of an action that already exists" in withAssetCleaner(wskprops) {
        val name = "dupeAction"
        val file = Some(TestUtils.getTestActionFilename("echo.js"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, file)
        }

        val stderr = wsk.action.create(name, file, expectedExitCode = CONFLICT).stderr
        verifyRejectCreateAction(stderr, name)
    }

    def verifyRejectCreateAction(stderr: String, name: String) = {
        val expectedString = s"""Unable to create action '$name': resource already exists \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "reject delete of action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.delete(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectDeleteAction(stderr, name)
    }

    def verifyRejectDeleteAction(stderr: String, name: String) = {
        val expectedString = s"""Unable to delete action '$name'. The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "reject invocation of action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.invoke(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectInvokeAction(stderr, name)
    }

    def verifyRejectInvokeAction(stderr: String, name: String) = {
        val expectedString = s"""Unable to invoke action '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "reject get of an action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.get(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectGetAction(stderr, name)
    }

    def verifyRejectGetAction(stderr: String, name: String) = {
        val expectedString = s"""Unable to get action '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    it should "create, and invoke an action that utilizes a docker container" in withAssetCleaner(wskprops) {
        val name = "dockerContainer"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            // this docker image will be need to be pulled from dockerhub and hence has to be published there first
            (action, _) => action.create(name, None, docker = Some("openwhisk/example"))
        }

        val args = Map("payload" -> "test".toJson)
        val run = wsk.action.invoke(name, args)
        withActivation(wsk.activation, run) {
            activation =>
                activation.response.result shouldBe Some(JsObject(
                    "args" -> args.toJson,
                    "msg" -> "Hello from arbitrary C program!".toJson))
        }
    }

    it should "create, and invoke an action that utilizes dockerskeleton with native zip" in withAssetCleaner(wskprops) {
        val name = "dockerContainerWithZip"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            // this docker image will be need to be pulled from dockerhub and hence has to be published there first
            (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("blackbox.zip")), kind = Some("native"))
        }

        val run = wsk.action.invoke(name, Map())
        withActivation(wsk.activation, run) {
            activation =>
                activation.response.result shouldBe Some(JsObject(
                    "msg" -> "hello zip".toJson))
                activation.logs shouldBe defined
                val logs = activation.logs.get.toString
                logs should include("This is an example zip used with the docker skeleton action.")
                logs should not include ("XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX")
        }
    }

    it should "create, and invoke an action using a parameter file" in withAssetCleaner(wskprops) {
        val name = "paramFileAction"
        val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
        val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, file)
        }

        val expectedOutput = JsObject("payload" -> JsString("test"))
        val run = wsk.action.invoke(name, parameterFile = argInput)
        withActivation(wsk.activation, run) {
            activation => activation.response.result shouldBe Some(expectedOutput)
        }
    }

    it should "create an action, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "actionFields"
        val paramInput = Map("payload" -> "test".toJson)

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, defaultAction, parameters = paramInput)
        }

        val expectedParam = JsObject("payload" -> JsString("test"))
        val ns = wsk.namespace.whois()

        verifyCreateActionGetField(name, ns)
    }

    def verifyCreateActionGetField(name: String, ns: String) = {
        val successMsg = s"ok: got action $name, displaying field"
        wsk.action.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
        wsk.action.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
        wsk.action.get(name, fieldFilter = Some("exec")).stdout should include(s"""$successMsg""")
        wsk.action.get(name, fieldFilter = Some("exec")).stdout should include regex (s"""$successMsg exec\n\\{\\s+"kind":\\s+"nodejs:6",\\s+"code":\\s+"\\/\\*\\*[\\\\r]*\\\\n \\* Hello, world.[\\\\r]*\\\\n \\*\\/[\\\\r]*\\\\nfunction main\\(params\\) \\{[\\\\r]*\\\\n    greeting \\= 'hello, ' \\+ params.payload \\+ '!'[\\\\r]*\\\\n    console.log\\(greeting\\);[\\\\r]*\\\\n    return \\{payload: greeting\\}[\\\\r]*\\\\n\\}""")
        wsk.action.get(name, fieldFilter = Some("parameters")).stdout should include regex (s"""$successMsg parameters\n\\[\\s+\\{\\s+"key":\\s+"payload",\\s+"value":\\s+"test"\\s+\\}\\s+\\]""")
        wsk.action.get(name, fieldFilter = Some("annotations")).stdout should include regex (s"""$successMsg annotations\n\\[\\s+\\{\\s+"key":\\s+"exec",\\s+"value":\\s+"nodejs:6"\\s+\\}\\s+\\]""")
        wsk.action.get(name, fieldFilter = Some("limits")).stdout should include regex (s"""$successMsg limits\n\\{\\s+"timeout":\\s+60000,\\s+"memory":\\s+256,\\s+"logs":\\s+10\\s+\\}""")
        wsk.action.get(name, fieldFilter = Some("namespace")).stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
        wsk.action.get(name, fieldFilter = Some("invalid"), expectedExitCode = MISUSE_EXIT).stderr should include("error: Invalid field filter 'invalid'.")
        wsk.action.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")

    }

    /**
     * Tests creating an action from a malformed js file. This should fail in
     * some way - preferably when trying to create the action. If not, then
     * surely when it runs there should be some indication in the logs. Don't
     * think this is true currently.
     */
    it should "create and invoke action with malformed js resulting in activation error" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "MALFORMED"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("malformed.js")))
            }

            val run = wsk.action.invoke(name, Map("payload" -> "whatever".toJson))
            withActivation(wsk.activation, run) {
                activation =>
                    activation.response.status shouldBe "action developer error"
                    // representing nodejs giving an error when given malformed.js
                    activation.response.result.get.toString should include("ReferenceError")
            }
    }

    it should "create and invoke a blocking action resulting in an application error response" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "applicationError"
            val strErrInput = Map("error" -> "Error message".toJson)
            val numErrInput = Map("error" -> 502.toJson)
            val boolErrInput = Map("error" -> true.toJson)

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("echo.js")))
            }

            Seq(strErrInput, numErrInput, boolErrInput) foreach { input =>
                verifyCreateInvokeBlockingActionError(input, name)
            }
    }

    def verifyCreateInvokeBlockingActionError(input: Map[String, JsValue], name: String) = {
        getJSONFromResponse(wsk.action.invoke(name, parameters = input, blocking = true, expectedExitCode = 246).stderr,
            wsk.isInstanceOf[Wsk]).
            fields("response").asJsObject.fields("result").asJsObject shouldBe input.toJson.asJsObject

        wsk.action.invoke(name, parameters = input, blocking = true, result = true, expectedExitCode = 246).
            stderr.parseJson.asJsObject shouldBe input.toJson.asJsObject
    }

    it should "create and invoke a blocking action resulting in an failed promise" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "errorResponseObject"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("asyncError.js")))
            }

            verifyCreateInvokeBlockingAction(name)
    }

    def verifyCreateInvokeBlockingAction(name: String) = {
        val stderr = wsk.action.invoke(name, blocking = true, expectedExitCode = 246).stderr
        ActivationResult.serdes.read(removeCLIHeader(stderr).parseJson).response.result shouldBe Some {
            JsObject("error" -> JsObject("msg" -> "failed activation on purpose".toJson))
        }
    }
    it should "invoke a blocking action and get only the result" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "basicInvoke"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("wc.js")))
            }

            verifyInvokeActionGetResult(name)
    }

    def verifyInvokeActionGetResult(name: String) = {
        wsk.action.invoke(name, Map("payload" -> "one two three".toJson), result = true)
            .stdout should include regex (""""count": 3""")
    }

    it should "create, and get an action summary" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "actionName"
            val annots = Map(
                "description" -> JsString("Action description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, defaultAction, annotations = annots)
            }

            val res = wsk.action.get(name, summary = true)
            val ns = wsk.namespace.whois()

            verifyCreateActionSpace(res, ns, name)
    }

    def verifyCreateActionSpace(res: RunResult, ns: String, name: String) = {
        val expectedString = s"(?i)action /$ns/$name: Action description\\s*\\(parameters: paramName1, paramName2\\)"
        verifyStdoutInclude(res.stdout, expectedString)
    }

    it should "create an action with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "action with spaces"

            val res = assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, defaultAction)
            }

            verifyCreateActionSpace(res, name)
    }

    def verifyCreateActionSpace(res: RunResult, name: String) = {
        val expectedString = s"ok: created action $name"
        verifyStdoutInclude(res.stdout, expectedString)
    }

    it should "create an action, and invoke an action that returns an empty JSON object" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "emptyJSONAction"

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, Some(TestUtils.getTestActionFilename("emptyJSONResult.js")))
            }

            verifyCreateInvokeActionEmptyJson(name)
    }

    def verifyCreateInvokeActionEmptyJson(name: String) = {
        val stdout = wsk.action.invoke(name, result = true).stdout
        stdout.parseJson.asJsObject shouldBe JsObject()
    }

    it should "create, and invoke an action that times out to ensure the proper response is received" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "sleepAction"
            val params = Map("payload" -> "100000".toJson)
            val allowedActionDuration = 120 seconds
            val res = assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, Some(TestUtils.getTestActionFilename("timeout.js")),
                        timeout = Some(allowedActionDuration))
                    action.invoke(name, parameters = params, blocking=true, result = true, expectedExitCode = ACCEPTED)
            }

            verifyTimeout(res)
    }

    def verifyTimeout(result: RunResult) = {
        val expectedString = s"""but the request has not yet finished"""
        verifyStdoutInclude(result.stderr, expectedString)
    }

    it should "create, and get docker action get ensure exec code is omitted" in withAssetCleaner(wskprops) {
        val name = "dockerContainer"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, None, docker = Some("fake-container"))
        }

        wsk.action.get(name).stdout should not include (""""code"""")
    }

    behavior of "Wsk Trigger CLI"

    it should "create, update, get, fire and list trigger" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "listTriggers"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name, parameters = params)
                    trigger.create(name, update = true)
            }
            val stdout = wsk.trigger.get(name).stdout
            verifyCreateTrigger(stdout)

            val dynamicParams = Map("t" -> "T".toJson)
            val run = wsk.trigger.fire(name, dynamicParams)
            withActivation(wsk.activation, run) {
                activation =>
                    verifyFireTrigger(activation, dynamicParams)
            }

            val runWithNoParams = wsk.trigger.fire(name, Map())
            withActivation(wsk.activation, runWithNoParams) {
                activation =>
                    verifyFireTriggerNoParam(activation)
            }

            wsk.trigger.list().stdout should include(name)
    }

    def verifyCreateTrigger(stdout: String) = {
        verifyStdoutInclude(stdout, """"key": "a"""")
        verifyStdoutInclude(stdout, """"value": "A"""")
        verifyStdoutInclude(stdout, """"publish": false""")
        verifyStdoutInclude(stdout, """"version": "0.0.2"""")
    }

    def verifyFireTrigger(activation: ActivationResult, dynamicParams: Map[String, JsValue]) = {
        activation.response.result shouldBe Some(dynamicParams.toJson)
        activation.duration shouldBe 0L // shouldn't exist but CLI generates it
        activation.end shouldBe Instant.EPOCH // shouldn't exist but CLI generates it
    }

    def verifyFireTriggerNoParam(activation: ActivationResult) = {
        activation.response.result shouldBe Some(JsObject())
        activation.duration shouldBe 0L // shouldn't exist but CLI generates it
        activation.end shouldBe Instant.EPOCH // shouldn't exist but CLI generates it
    }

    it should "create, and get a trigger summary" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "triggerName"
            val annots = Map(
                "description" -> JsString("Trigger description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name, annotations = annots)
            }

            val result = wsk.trigger.get(name, summary = true)
            val ns = wsk.namespace.whois()

            verifyCreateGetTrigger(result, name, ns)
    }

    def verifyCreateGetTrigger(result: RunResult, name: String, ns: String) = {
          result.stdout should include regex (s"trigger /$ns/$name: Trigger description\\s*\\(parameters: paramName1, paramName2\\)")
    }

    it should "create a trigger with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "trigger with spaces"

            val res = assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }

            verifyCreateTriggerSpace(res, name)
    }

    def verifyCreateTriggerSpace(result: RunResult, name: String) = {
        result.stdout should include regex (s"ok: created trigger $name")
    }

    it should "create, and fire a trigger using a parameter file" in withAssetCleaner(wskprops) {
        val name = "paramFileTrigger"
        val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
        val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name)
        }

        val expectedOutput = JsObject("payload" -> JsString("test"))
        val run = wsk.trigger.fire(name, parameterFile = argInput)
        withActivation(wsk.activation, run) {
            activation => activation.response.result shouldBe Some(expectedOutput)
        }
    }

    it should "create a trigger, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "triggerFields"
        val paramInput = Map("payload" -> "test".toJson)
        val successMsg = s"ok: got trigger $name, displaying field"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name, parameters = paramInput)
        }

        val expectedParam = JsObject("payload" -> JsString("test"))
        val ns = wsk.namespace.whois()

        verifyCreateTriggerGetField(name, ns)
    }

    def verifyCreateTriggerGetField(name: String, ns: String) = {
        val successMsg = s"ok: got trigger $name, displaying field"
        wsk.trigger.get(name, fieldFilter = Some("namespace")).stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
        wsk.trigger.get(name, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
        wsk.trigger.get(name, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
        wsk.trigger.get(name, fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
        wsk.trigger.get(name, fieldFilter = Some("annotations")).stdout should include(s"""$successMsg annotations\n[]""")
        wsk.trigger.get(name, fieldFilter = Some("parameters")).stdout should include regex (s"""$successMsg parameters\n\\[\\s+\\{\\s+"key":\\s+"payload",\\s+"value":\\s+"test"\\s+\\}\\s+\\]""")
        wsk.trigger.get(name, fieldFilter = Some("limits")).stdout should include(s"""$successMsg limits\n{}""")
        wsk.trigger.get(name, fieldFilter = Some("invalid"), expectedExitCode = ERROR_EXIT).stderr should include("error: Invalid field filter 'invalid'.")

    }

    it should "create, and fire a trigger to ensure result is empty" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "emptyResultTrigger"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }

            val run = wsk.trigger.fire(name)
            withActivation(wsk.activation, run) {
                activation => activation.response.result shouldBe Some(JsObject())
            }
    }

    it should "reject creation of duplicate triggers" in withAssetCleaner(wskprops) {
        val name = "dupeTrigger"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name)
        }

        val stderr = wsk.trigger.create(name, expectedExitCode = CONFLICT).stderr
        verifyRejectCreateTrigger(stderr, name)
    }

    it should "reject delete of trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.delete(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectDeleteTrigger(stderr, name)
    }

    it should "reject get of trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.get(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectGetTrigger(stderr, name)
    }

    it should "reject firing of a trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.fire(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectFireTrigger(stderr, name)
    }

    def verifyRejectCreateTrigger(stderr: String, name: String) = {
        val expectedString = s"""Unable to create trigger '$name': resource already exists \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectDeleteTrigger(stderr: String, name: String) = {
        val expectedString = s"""Unable to get trigger '$name'. The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectGetTrigger(stderr: String, name: String) = {
        val expectedString = s"""Unable to get trigger '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectFireTrigger(stderr: String, name: String) = {
        val expectedString = s"""Unable to fire trigger '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    behavior of "Wsk Rule CLI"

    it should "create rule, get rule, update rule and list rule" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "listRules"
            val triggerName = "listRulesTrigger"
            val actionName = "listRulesAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName) {
                (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
            }

            // finally, we perform the update, and expect success this time
            wsk.rule.create(ruleName, trigger = triggerName, action = actionName, update = true)

            val rule = wsk.rule.get(ruleName)
            val ruleList = wsk.rule.list()
            verifyCreateListRule(rule, ruleList, ruleName, triggerName, actionName)
    }

    def verifyCreateListRule(rule: RunResult, ruleList: RunResult, ruleName: String, triggerName: String, actionName: String) = {
        val stdout = rule.stdout
        stdout should include(ruleName)
        stdout should include(triggerName)
        stdout should include(actionName)
        stdout should include regex (""""version": "0.0.2"""")
        ruleList.stdout should include(ruleName)
    }

    it should "create rule, get rule, ensure rule is enabled by default" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "enabledRule"
            val triggerName = "enabledRuleTrigger"
            val actionName = "enabledRuleAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName) {
                (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
            }

            val stdout = wsk.rule.get(ruleName).stdout
            stdout should include regex (""""status":\s*"active"""")
    }

    it should "display a rule summary when --summary flag is used with 'wsk rule get'" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "mySummaryRule"
            val triggerName = "summaryRuleTrigger"
            val actionName = "summaryRuleAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName, confirmDelete = false) {
                (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
            }

            // Summary namespace should match one of the allowable namespaces (typically 'guest')
            val ns = wsk.namespace.whois()
            val rule = wsk.rule.get(ruleName, summary = true)

            verifyDisplayRuleSummary(rule, ns, ruleName)
    }

    def verifyDisplayRuleSummary(rule: RunResult, ns: String, ruleName: String) = {
        rule.stdout should include regex (s"(?i)rule /$ns/$ruleName\\s*\\(status: active\\)")
    }

    it should "create a rule, and get its individual fields" in withAssetCleaner(wskprops) {
        val ruleName = "ruleFields"
        val triggerName = "ruleTriggerFields"
        val actionName = "ruleActionFields"
        val paramInput = Map("payload" -> "test".toJson)


        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, triggerName) {
            (trigger, name) => trigger.create(name)
        }
        assetHelper.withCleaner(wsk.action, actionName) {
            (action, name) => action.create(name, defaultAction)
        }
        assetHelper.withCleaner(wsk.rule, ruleName) {
            (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
        }

        val ns = wsk.namespace.whois()
        verifyCreateRuleGetField(ns, ruleName, triggerName, actionName)
    }

    def verifyCreateRuleGetField(ns: String, ruleName: String, triggerName: String, actionName: String) = {
        val successMsg = s"ok: got rule $ruleName, displaying field"
        wsk.rule.get(ruleName, fieldFilter = Some("namespace")).stdout should include regex (s"""(?i)$successMsg namespace\n"$ns"""")
        wsk.rule.get(ruleName, fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$ruleName"""")
        wsk.rule.get(ruleName, fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"\n""")
        wsk.rule.get(ruleName, fieldFilter = Some("status")).stdout should include(s"""$successMsg status\n"active"""")
        val trigger = wsk.rule.get(ruleName, fieldFilter = Some("trigger")).stdout
        trigger should include regex (s"""$successMsg trigger\n""")
        trigger should include(triggerName)
        trigger should not include (actionName)
        val action = wsk.rule.get(ruleName, fieldFilter = Some("action")).stdout
        action should include regex (s"""$successMsg action\n""")
        action should include(actionName)
        action should not include (triggerName)
    }

    it should "reject creation of duplicate rules" in withAssetCleaner(wskprops) {
        val ruleName = "dupeRule"
        val triggerName = "triggerName"
        val actionName = "actionName"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, triggerName) {
            (trigger, name) => trigger.create(name)
        }
        assetHelper.withCleaner(wsk.action, actionName) {
            (action, name) => action.create(name, defaultAction)
        }
        assetHelper.withCleaner(wsk.rule, ruleName) {
            (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
        }

        val stderr = wsk.rule.create(ruleName, trigger = triggerName, action = actionName, expectedExitCode = CONFLICT).stderr
        verifyRejectCreateRule(stderr, ruleName)
    }

    it should "reject delete of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.delete(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectDeleteRule(stderr, name)
    }

    it should "reject enable of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.enable(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectEnableRule(stderr, name)
    }

    it should "reject disable of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.disable(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectDisableRule(stderr, name)
    }

    it should "reject status of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.state(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectStatusRule(stderr, name)
    }

    it should "reject get of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.get(name, expectedExitCode = NOT_FOUND).stderr
        verifyRejectGetRule(stderr, name)
    }

    def verifyRejectCreateRule(stderr: String, ruleName: String) = {
        val expectedString = s"""Unable to create rule '$ruleName': resource already exists \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectDeleteRule(stderr: String, name: String) = {
        val expectedString = s"""Unable to delete rule '$name'. The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectEnableRule(stderr: String, name: String) = {
        val expectedString = s"""Unable to enable rule '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectDisableRule(stderr: String, name: String) = {
        val expectedString = s"""Unable to disable rule '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectStatusRule(stderr: String, name: String) = {
        val expectedString = s"""Unable to get status of rule '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    def verifyRejectGetRule(stderr: String, name: String) = {
        val expectedString = s"""Unable to get rule '$name': The requested resource does not exist. \\(code \\d+\\)"""
        verifyStdoutInclude(stderr, expectedString)
    }

    behavior of "Wsk Namespace CLI"

    it should "return a list of exactly one namespace" in {
        returnListForNS()
    }

    def returnListForNS() = {
        val lines = wsk.namespace.list().stdout.lines.toSeq
        lines should have size 2
        lines.head shouldBe "namespaces"
        lines(1).trim should not be empty
    }

    it should "list entities in default namespace" in {
        // use a fresh wsk props instance that is guaranteed to use
        // the default namespace
        listEntitiesDefaultNS()
    }

    def listEntitiesDefaultNS() = {
        wsk.namespace.get(expectedExitCode = SUCCESS_EXIT)(WskProps()).
            stdout should include("default")
    }

    it should "not list entities with an invalid namespace" in {
        val namespace = "fakeNamespace"
        val stderr = wsk.namespace.get(Some(s"/${namespace}"), expectedExitCode = FORBIDDEN).stderr

        verifyNotListEntities(stderr, namespace)
    }

    def verifyNotListEntities(stderr: String, namespace: String) = {
        stderr should include(s"Unable to obtain the list of entities for namespace '${namespace}'")
    }

    behavior of "Wsk Activation CLI"

    it should "create a trigger, and fire a trigger to get its individual fields from an activation" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "activationFields"

            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name)
            }

            val ns = wsk.namespace.whois()
            val run = wsk.trigger.fire(name)
            withActivation(wsk.activation, run) {
                activation =>
                    verifyTriggerActivation(activation, ns, name)
            }
    }

    def verifyTriggerActivation(activation: ActivationResult, namespace: String, name: String) = {
        val ns = s""""$namespace""""
        val successMsg = s"ok: got activation ${activation.activationId}, displaying field"
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("namespace")).stdout should include regex (s"""(?i)$successMsg namespace\n$ns""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("name")).stdout should include(s"""$successMsg name\n"$name"""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("version")).stdout should include(s"""$successMsg version\n"0.0.1"""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("publish")).stdout should include(s"""$successMsg publish\nfalse""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("subject")).stdout should include regex (s"""(?i)$successMsg subject\n""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("activationid")).stdout should include(s"""$successMsg activationid\n"${activation.activationId}""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("start")).stdout should include regex (s"""$successMsg start\n\\d""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("end")).stdout should include regex (s"""$successMsg end\n\\d""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("duration")).stdout should include regex (s"""$successMsg duration\n\\d""")
        wsk.activation.get(Some(activation.activationId), fieldFilter = Some("annotations")).stdout should include(s"""$successMsg annotations\n[]""")
    }

    it should "reject get of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.get(Some(name), expectedExitCode = NOT_FOUND).stderr
        verifyRejectLGet(stderr, name)
    }

    def verifyRejectLGet(stderr: String, name: String) = {
        stderr should include regex (s"""Unable to get activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
    }

    it should "reject logs of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.logs(Some(name), expectedExitCode = NOT_FOUND).stderr
        verifyRejectLog(stderr, name)
    }

    def verifyRejectLog(stderr: String, name: String) = {
        stderr should include regex (s"""Unable to get logs for activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
    }

    it should "reject result of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.result(Some(name), expectedExitCode = NOT_FOUND).stderr
        verifyRejectResult(stderr, name)
    }

    def verifyRejectResult(stderr: String, name: String) = {
        stderr should include regex (s"""Unable to get result for activation '$name': The requested resource does not exist. \\(code \\d+\\)""")
    }

    it should "reject activation request when using activation ID with --last Flag" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val auth: Seq[String] = Seq("--auth", wskprops.authKey)

            val lastId = "dummyActivationId"
            val tooManyArgsMsg = s"${lastId}. An activation ID is required."
            val invalidField = s"Invalid field filter '${lastId}'."

            val invalidCmd = Seq(
                (Seq("activation", "get", s"$lastId", "publish", "--last"), tooManyArgsMsg),
                (Seq("activation", "get", s"$lastId", "--last"), invalidField),
                (Seq("activation", "logs", s"$lastId", "--last"), tooManyArgsMsg),
                (Seq("activation", "result", s"$lastId", "--last"), tooManyArgsMsg))

            invalidCmd foreach {
                case (cmd, err) =>
                    val stderr = wsk.cli(cmd ++ wskprops.overrides ++ auth, expectedExitCode = ERROR_EXIT).stderr
                    stderr should include(err)
            }
    }
>>>>>>> 7e0c0e9... Replace the test cases with REST implementation
}
