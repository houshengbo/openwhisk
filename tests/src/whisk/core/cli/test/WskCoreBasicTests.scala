/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.cli.test

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.BeforeAndAfterAll

import common.RunWskAdminCmd
import common.TestHelpers
import common.TestUtils
import common.TestUtils.ANY_ERROR_EXIT
import common.TestUtils.FORBIDDEN
import common.TestUtils.NOT_FOUND
import common.TestUtils.TIMEOUT
import common.Wsk
import common.WskProps
import common.WskTestHelpers
import spray.json.DefaultJsonProtocol._
import spray.json.pimpAny
import whisk.core.entity.Subject
import whisk.core.entity.WhiskPackage

@RunWith(classOf[JUnitRunner])
class WskCoreBasicTests
    extends TestHelpers
    with WskTestHelpers
    with BeforeAndAfterAll {

    val originWskProps = WskProps()
    val wsk = new Wsk(usePythonCLI = false)
    val samplePackage = "samplePackage"
    val sampleAction = s"$samplePackage/sampleAction"
    val sampleFeed = s"$samplePackage/sampleFeed"
    val wskadmin = new RunWskAdminCmd {}

    val otherNamespace = Subject().toString()
    val create = wskadmin.cli(Seq("user", "create", otherNamespace))
    val otherAuthkey = create.stdout.trim
    implicit val otherWskProps = WskProps(namespace = otherNamespace, authKey = otherAuthkey)

    override def afterAll() = {
        withClue(s"failed to delete temporary namespace $otherNamespace") {
            wskadmin.cli(Seq("user", "delete", otherNamespace)).stdout should include("Subject deleted")
        }
    }

    behavior of "Wsk CLI"

    it should "reject deleting action in shared package not owned by authkey" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            assetHelper.withCleaner(wsk.action, sampleAction) {
                val file = Some(TestUtils.getTestActionFilename("empty.js"))
                (action, _) => action.create(sampleAction, file, kind = Some("nodejs"), shared = Some(true))
            }
            val fullyQualifiedActionName = "/" + otherNamespace + "/" + sampleAction
            wsk.action.get(fullyQualifiedActionName)(originWskProps)
            wsk.action.delete(fullyQualifiedActionName, expectedExitCode = FORBIDDEN)(originWskProps)
    }

    it should "reject create action in shared package not owned by authkey" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, name) => pkg.create(name, shared = Some(true))
            }
            val fullyQualifiedActionName = s"/$otherNamespace/notallowed"
            val file = Some(TestUtils.getTestActionFilename("empty.js"))
            assetHelper.withCleaner(wsk.action, fullyQualifiedActionName, confirmDelete = false) {
                 (action, name) => action.create(name, file, expectedExitCode = FORBIDDEN)(originWskProps) 
            }
    }

    it should "reject update action in shared package not owned by authkey" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            assetHelper.withCleaner(wsk.action, sampleAction) {
                val file = Some(TestUtils.getTestActionFilename("empty.js"))
                (action, _) => action.create(sampleAction, file, kind = Some("nodejs"), shared = Some(true))
            }
            val fullyQualifiedActionName = s"/$otherNamespace/notallowed"
            wsk.action.create(fullyQualifiedActionName, None,
                              update = true, shared = Some(true), expectedExitCode = FORBIDDEN)(originWskProps)
    }

    behavior of "Wsk Package CLI"

    it should "list shared packages" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            val fullyQualifiedPackageName = s"/$otherNamespace/$samplePackage"
            val result = wsk.pkg.list(Some(s"/$otherNamespace"))(originWskProps).stdout
            result should include regex (fullyQualifiedPackageName + """\s+shared""")
    }

    it should "list shared package actions" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            assetHelper.withCleaner(wsk.action, sampleAction) {
                val file = Some(TestUtils.getTestActionFilename("empty.js"))
                (action, _) => action.create(sampleAction, file, kind = Some("nodejs"), shared = Some(true))
            }
            val fullyQualifiedPackageName = s"/$otherNamespace/$samplePackage"
            val fullyQualifiedActionName = s"/$otherNamespace/$sampleAction"
            val result = wsk.action.list(Some(fullyQualifiedPackageName))(originWskProps).stdout
            result should include regex (fullyQualifiedActionName + """\s+shared""")
    }

    it should "create a package binding" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            val fullyQualifiedPackageName = s"/$otherNamespace/$samplePackage"
            val name = "bindPackage"
            val provider = fullyQualifiedPackageName
            val annotations = Map("a" -> "A".toJson, WhiskPackage.bindingFieldName -> "xxx".toJson)
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) =>
                    pkg.bind(provider, name, annotations = annotations)
            }
            val stdout = wsk.pkg.get(name).stdout
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (s""""key": "${WhiskPackage.bindingFieldName}"""")
            stdout should not include regex(""""key": "xxx"""")
    }

    behavior of "Wsk Action CLI"

    it should "get an action" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            assetHelper.withCleaner(wsk.action, sampleAction) {
                val file = Some(TestUtils.getTestActionFilename("empty.js"))
                (action, _) => action.create(sampleAction, file, kind = Some("nodejs"), shared = Some(true))
            }
            val fullyQualifiedActionName = s"/$otherNamespace/$sampleAction"
            val stdout = wsk.action.get(fullyQualifiedActionName)(originWskProps).stdout
            stdout should include("name")
            stdout should include("parameters")
            stdout should include("limits")
    }

    behavior of "Wsk Trigger CLI"

    it should "not create a trigger with timeout error when feed fails to initialize" in withAssetCleaner(otherWskProps) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, samplePackage) {
                (pkg, _) =>
                    pkg.create(samplePackage, parameters = Map("a" -> "A".toJson), shared = Some(true),
                               update = true)
            }
            assetHelper.withCleaner(wsk.action, sampleFeed) {
                val file = Some(TestUtils.getTestActionFilename("empty.js"))
                (action, _) => action.create(sampleFeed, file, kind = Some("nodejs"), shared = Some(true))
            }
            val fullyQualifiedFeedName = s"/$otherNamespace/$sampleFeed"
            assetHelper.withCleaner(wsk.trigger, "badfeed", confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"bogus"), expectedExitCode = ANY_ERROR_EXIT)(originWskProps).
                        exitCode should { equal(NOT_FOUND) or equal(FORBIDDEN) }
                    trigger.get(name, expectedExitCode = NOT_FOUND)

                    trigger.create(name, feed = Some(s"bogus/feed"), expectedExitCode = ANY_ERROR_EXIT)(originWskProps).
                        exitCode should { equal(NOT_FOUND) or equal(FORBIDDEN) }
                    trigger.get(name, expectedExitCode = NOT_FOUND)
                    // verify that the feed runs and returns an application error (502 or Gateway Timeout)
                    trigger.create(name, feed = Some(fullyQualifiedFeedName), expectedExitCode = TIMEOUT)(originWskProps)
                    trigger.get(name, expectedExitCode = NOT_FOUND)(originWskProps)
            }
    }

}
