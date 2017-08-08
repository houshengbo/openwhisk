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

import common.Wsk

@RunWith(classOf[JUnitRunner])
<<<<<<< HEAD:tests/src/test/scala/system/basic/WskBasicSwift311Tests.scala
class WskBasicSwift311Tests extends WskBasicSwift3Tests {

  override lazy val currentSwiftDefaultKind = "swift:3.1.1"
=======
class WskCliBasicSwift311Tests
    extends WskBasicSwift3Tests {

    override lazy val currentSwiftDefaultKind = "swift:3.1.1"
    override val wsk = new Wsk
>>>>>>> 7e0c0e9... Replace the test cases with REST implementation:tests/src/test/scala/system/basic/WskCliBasicSwift311Tests.scala
}
