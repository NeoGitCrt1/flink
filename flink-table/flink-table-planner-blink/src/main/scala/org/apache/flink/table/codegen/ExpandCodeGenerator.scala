/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.codegen

import org.apache.flink.table.`type`.RowType
import org.apache.flink.table.api.TableConfig
import org.apache.flink.table.dataformat.{BaseRow, BoxedWrapperRow}
import org.apache.flink.table.runtime.CodeGenOperatorFactory

import org.apache.calcite.rex.RexNode

import scala.collection.JavaConversions._
import scala.collection.mutable

object ExpandCodeGenerator {

  def generateExpandOperator(
      ctx: CodeGeneratorContext,
      inputType: RowType,
      outputType: RowType,
      config: TableConfig,
      projects: java.util.List[java.util.List[RexNode]],
      retainHeader: Boolean = false,
      opName: String): CodeGenOperatorFactory[BaseRow] = {
    val inputTerm = CodeGenUtils.DEFAULT_INPUT1_TERM

    val exprGenerator = new ExprCodeGenerator(ctx, false)
      .bindInput(inputType, inputTerm = inputTerm)

    val processCodes = mutable.ListBuffer[String]()
    projects.foreach { project =>
      val projectionExprs = project.map(exprGenerator.generateExpression)
      val projectionResultExpr = exprGenerator.generateResultExpression(
        projectionExprs, outputType, classOf[BoxedWrapperRow])
      val header = if (retainHeader) {
        s"${projectionResultExpr.resultTerm}.setHeader($inputTerm.getHeader());"
      } else {
        ""
      }

      processCodes += header
      processCodes += projectionResultExpr.code
      processCodes += OperatorCodeGenerator.generateCollect(projectionResultExpr.resultTerm)
    }

    val processCode = processCodes.mkString("\n")

    val genOperator = OperatorCodeGenerator.generateOneInputStreamOperator[BaseRow, BaseRow](
      ctx,
      opName,
      processCode,
      "",
      inputType,
      config,
      inputTerm = inputTerm,
      lazyInputUnboxingCode = false)

    new CodeGenOperatorFactory(genOperator)
  }

}
