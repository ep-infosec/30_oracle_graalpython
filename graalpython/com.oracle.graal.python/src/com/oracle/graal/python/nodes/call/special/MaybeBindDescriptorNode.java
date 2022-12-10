/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Bind the descriptor to the receiver unless it's one of the descriptor types that our method call
 * nodes handle unbound.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class MaybeBindDescriptorNode extends PNodeWithContext {

    public abstract Object execute(Frame frame, Object descriptor, Object receiver, Object receiverType);

    @Specialization(guards = "isNoValue(descriptor)")
    static Object doNoValue(Object descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(BuiltinMethodDescriptor descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(PBuiltinFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(PBuiltinMethod descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return new BoundDescriptor(descriptor);
    }

    @Specialization
    static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    public static boolean isMethodDescriptor(Object descriptor) {
        return descriptor instanceof BuiltinMethodDescriptor || descriptor instanceof PBuiltinFunction || descriptor instanceof PFunction;
    }

    protected static boolean needsToBind(Object descriptor) {
        return !(descriptor == PNone.NO_VALUE || isMethodDescriptor(descriptor));
    }

    @Specialization(guards = "needsToBind(descriptor)")
    static Object doBind(VirtualFrame frame, Object descriptor, Object receiver, Object receiverType,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet,
                    @Cached CallTernaryMethodNode callGet) {
        Object getMethod = lookupGet.execute(getClassNode.execute(descriptor));
        if (getMethod != PNone.NO_VALUE) {
            return new BoundDescriptor(callGet.execute(frame, getMethod, descriptor, receiver, receiverType));
        }
        // CPython considers non-descriptors already bound
        return new BoundDescriptor(descriptor);
    }

    public static MaybeBindDescriptorNode create() {
        return MaybeBindDescriptorNodeGen.create();
    }

    public static MaybeBindDescriptorNode getUncached() {
        return MaybeBindDescriptorNodeGen.getUncached();
    }
}
