/*
*Copyright (c) 2021, Alibaba Group;
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

*   http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright Havenask Contributors. See
 * GitHub history for details.
 */

package org.havenask.painless.ir;

import org.havenask.painless.ClassWriter;
import org.havenask.painless.Location;
import org.havenask.painless.MethodWriter;
import org.havenask.painless.Operation;
import org.havenask.painless.phase.IRTreeVisitor;
import org.havenask.painless.symbol.WriteScope;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

public class BooleanNode extends BinaryNode {

    /* ---- begin node data ---- */

    private Operation operation;

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public Operation getOperation() {
        return operation;
    }

    /* ---- end node data, begin visitor ---- */

    @Override
    public <Scope> void visit(IRTreeVisitor<Scope> irTreeVisitor, Scope scope) {
        irTreeVisitor.visitBoolean(this, scope);
    }

    @Override
    public <Scope> void visitChildren(IRTreeVisitor<Scope> irTreeVisitor, Scope scope) {
        getLeftNode().visit(irTreeVisitor, scope);
        getRightNode().visit(irTreeVisitor, scope);
    }

    /* ---- end visitor ---- */

    public BooleanNode(Location location) {
        super(location);
    }

    @Override
    protected void write(ClassWriter classWriter, MethodWriter methodWriter, WriteScope writeScope) {
        methodWriter.writeDebugInfo(getLocation());

        if (operation == Operation.AND) {
            Label fals = new Label();
            Label end = new Label();

            getLeftNode().write(classWriter, methodWriter, writeScope);
            methodWriter.ifZCmp(Opcodes.IFEQ, fals);
            getRightNode().write(classWriter, methodWriter, writeScope);
            methodWriter.ifZCmp(Opcodes.IFEQ, fals);

            methodWriter.push(true);
            methodWriter.goTo(end);
            methodWriter.mark(fals);
            methodWriter.push(false);
            methodWriter.mark(end);
        } else if (operation == Operation.OR) {
            Label tru = new Label();
            Label fals = new Label();
            Label end = new Label();

            getLeftNode().write(classWriter, methodWriter, writeScope);
            methodWriter.ifZCmp(Opcodes.IFNE, tru);
            getRightNode().write(classWriter, methodWriter, writeScope);
            methodWriter.ifZCmp(Opcodes.IFEQ, fals);

            methodWriter.mark(tru);
            methodWriter.push(true);
            methodWriter.goTo(end);
            methodWriter.mark(fals);
            methodWriter.push(false);
            methodWriter.mark(end);
        } else {
            throw new IllegalStateException("unexpected boolean operation [" + operation + "] " +
                    "for type [" + getExpressionCanonicalTypeName() + "]");
        }
    }
}
