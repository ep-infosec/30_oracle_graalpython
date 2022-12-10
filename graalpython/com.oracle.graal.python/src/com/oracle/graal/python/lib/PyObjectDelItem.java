/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent to use for PyObject_DelItem.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectDelItem extends Node {
    public abstract void execute(Frame frame, Object container, Object index);

    @Specialization
    static void doWithFrame(VirtualFrame frame, Object primary, Object index,
                    @Shared("getclass") @Cached GetClassNode getClassNode,
                    @Cached("create(DelItem)") LookupSpecialMethodSlotNode lookupDelitem,
                    @Shared("raiseNode") @Cached PRaiseNode raise,
                    @Shared("callNode") @Cached CallBinaryMethodNode callDelitem) {
        Object delitem = lookupDelitem.execute(frame, getClassNode.execute(primary), primary);
        if (delitem == PNone.NO_VALUE) {
            throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
        }
        callDelitem.executeObject(frame, delitem, primary, index);
    }

    @Specialization(replaces = "doWithFrame")
    static void doGeneric(Object primary, Object index,
                    @Shared("getclass") @Cached GetClassNode getClassNode,
                    @Cached(parameters = "DelItem") LookupCallableSlotInMRONode lookupDelitem,
                    @Shared("raiseNode") @Cached PRaiseNode raise,
                    @Shared("callNode") @Cached CallBinaryMethodNode callDelitem) {
        Object setitem = lookupDelitem.execute(getClassNode.execute(primary));
        if (setitem == PNone.NO_VALUE) {
            throw raise.raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, primary);
        }
        callDelitem.executeObject(null, setitem, primary, index);
    }

    public static PyObjectDelItem create() {
        return PyObjectDelItemNodeGen.create();
    }

    public static PyObjectDelItem getUncached() {
        return PyObjectDelItemNodeGen.getUncached();
    }
}
