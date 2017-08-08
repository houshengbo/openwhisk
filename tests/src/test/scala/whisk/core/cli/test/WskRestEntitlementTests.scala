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

package whisk.core.cli.test

import akka.http.scaladsl.model.StatusCodes.OK

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import spray.json.JsObject

import common.rest.WskRest
import common.rest.RestResult

@RunWith(classOf[JUnitRunner])
class WskRestEntitlementTests
    extends WskEntitlementTests {

    override val wsk = new WskRest

    override def verifyGetInvokeAction(stdout: String) = {
        stdout should include("name")
        stdout should include("parameters")
        stdout should include("limits")
        stdout should include regex (""""key":"a"""")
        stdout should include regex (""""value":"A"""")
    }

    override def verifyListSharedPackages(guestNamespace: String, samplePackage: String) = {
        val fullyQualifiedPackageName = s"/$guestNamespace/$samplePackage"
        val result = wsk.pkg.list(Some(s"/$guestNamespace"))(defaultWskProps)
        val list = result.getBodyListJsObject()
        var found = false
        list.foreach((obj: JsObject) =>
            if ((RestResult.getField(obj, "name") == samplePackage) &&
                (RestResult.getFieldJsValue(obj, "publish").toString == "true") &&
                    (RestResult.getField(obj, "namespace") == guestNamespace))
                found = true
        )
        found shouldBe true
    }

    override def verifyListSharedPackageActions(guestNamespace: String,
        samplePackage: String, fullSampleActionName: String) = {
        val fullyQualifiedPackageName = s"/$guestNamespace/$samplePackage"
        val fullyQualifiedPackageNameExpected = s"$guestNamespace/$samplePackage"
        val result = wsk.action.list(Some(fullyQualifiedPackageName))(defaultWskProps)
        result.statusCode shouldBe OK
        val list = result.getBodyListJsObject()
        var found = false
        list.foreach((obj: JsObject) =>
            if ((RestResult.getField(obj, "name") == sampleAction) &&
                (RestResult.getField(obj, "namespace") == fullyQualifiedPackageNameExpected))
                    found = true
        )
        found shouldBe true
    }
}
