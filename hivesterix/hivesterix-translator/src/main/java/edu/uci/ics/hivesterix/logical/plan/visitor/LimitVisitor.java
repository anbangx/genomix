/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hivesterix.logical.plan.visitor;

import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.hadoop.hive.ql.exec.LimitOperator;
import org.apache.hadoop.hive.ql.plan.LimitDesc;

import edu.uci.ics.hivesterix.logical.expression.HivesterixConstantValue;
import edu.uci.ics.hivesterix.logical.plan.visitor.base.DefaultVisitor;
import edu.uci.ics.hivesterix.logical.plan.visitor.base.Translator;
import edu.uci.ics.hivesterix.runtime.jobgen.Schema;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;

public class LimitVisitor extends DefaultVisitor {

    @Override
    public Mutable<ILogicalOperator> visit(LimitOperator operator,
            Mutable<ILogicalOperator> AlgebricksParentOperatorRef, Translator t) {
        Schema currentSchema = t.generateInputSchema(operator.getParentOperators().get(0));

        LimitDesc desc = (LimitDesc) operator.getConf();
        int limit = desc.getLimit();
        Integer limitValue = new Integer(limit);

        ILogicalExpression expr = new ConstantExpression(new HivesterixConstantValue(limitValue));
        ILogicalOperator currentOperator = new edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.LimitOperator(
                expr, true);
        currentOperator.getInputs().add(AlgebricksParentOperatorRef);

        operator.setSchema(operator.getParentOperators().get(0).getSchema());
        List<LogicalVariable> latestOutputSchema = t.getVariablesFromSchema(currentSchema);
        t.rewriteOperatorOutputSchema(latestOutputSchema, operator);
        return new MutableObject<ILogicalOperator>(currentOperator);
    }

}
