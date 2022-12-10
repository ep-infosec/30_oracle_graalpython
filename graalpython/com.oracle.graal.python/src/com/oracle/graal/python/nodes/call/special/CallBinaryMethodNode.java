/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class CallBinaryMethodNode extends CallReversibleMethodNode {
    public static CallBinaryMethodNode create() {
        return CallBinaryMethodNodeGen.create();
    }

    public static CallBinaryMethodNode getUncached() {
        return CallBinaryMethodNodeGen.getUncached();
    }

    public abstract boolean executeBool(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract int executeInt(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract long executeLong(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract Object executeObject(Frame frame, Object callable, Object arg1, Object arg2);

    public final Object executeObject(Object callable, Object arg1, Object arg2) {
        return executeObject(null, callable, arg1, arg2);
    }

    @Specialization(guards = {"cachedInfo == info", "node != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callBinarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") BinaryBuiltinDescriptor info, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("info") BinaryBuiltinDescriptor cachedInfo,
                    @Cached("getBuiltin(cachedInfo)") PythonBinaryBuiltinNode node) {
        if (cachedInfo.isReverseOperation()) {
            return node.execute(frame, arg2, arg1);
        } else {
            return node.execute(frame, arg1, arg2);
        }
    }

    protected static boolean hasAllowedArgsNum(BuiltinMethodDescriptor descr) {
        return descr.minNumOfPositionalArgs() <= 2;
    }

    @Specialization(guards = {"cachedInfo == info", "node != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callTernarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") TernaryBuiltinDescriptor info, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("info") TernaryBuiltinDescriptor cachedInfo,
                    @Cached("hasAllowedArgsNum(cachedInfo)") boolean hasValidArgsNum,
                    @Cached("getBuiltin(cachedInfo)") PythonTernaryBuiltinNode node) {
        raiseInvalidArgsNumUncached(hasValidArgsNum, cachedInfo);
        if (cachedInfo.isReverseOperation()) {
            return node.execute(frame, arg2, arg1, PNone.NO_VALUE);
        }
        return node.execute(frame, arg1, arg2, PNone.NO_VALUE);
    }

    protected static boolean isBinaryOrTernaryBuiltinDescriptor(Object value) {
        return value instanceof BinaryBuiltinDescriptor || value instanceof TernaryBuiltinDescriptor;
    }

    @Specialization(guards = "isBinaryOrTernaryBuiltinDescriptor(info)", replaces = {"callBinarySpecialMethodSlotInlined", "callTernarySpecialMethodSlotInlined"})
    Object callSpecialMethodSlotCallTarget(VirtualFrame frame, BuiltinMethodDescriptor info, Object arg1, Object arg2,
                    @Cached ConditionProfile invalidArgsProfile,
                    @Cached GenericInvokeNode invokeNode) {
        raiseInvalidArgsNumUncached(invalidArgsProfile.profile(hasAllowedArgsNum(info)), info);
        RootCallTarget callTarget = PythonLanguage.get(this).getDescriptorCallTarget(info);
        Object[] arguments = PArguments.create(2);
        PArguments.setArgument(arguments, 0, arg1);
        PArguments.setArgument(arguments, 1, arg2);
        return invokeNode.execute(frame, callTarget, arguments);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObjectSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBuiltin(frame, func, 2)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        if (isReverse) {
            return callBinaryBuiltin(frame, builtinNode, arg2, arg1);
        } else {
            return callBinaryBuiltin(frame, builtinNode, arg1, arg2);
        }
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "frame != null || unusedFrame"}, //
                    limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObject(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBuiltin(frame, func, 2)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        if (isReverse) {
            return callBinaryBuiltin(frame, builtinNode, arg2, arg1);
        } else {
            return callBinaryBuiltin(frame, builtinNode, arg1, arg2);
        }
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getFunction(), 2)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return callBinaryBuiltin(frame, builtinNode, arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getFunction(), 2)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return callBinaryBuiltin(frame, builtinNode, arg1, arg2);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callMethodSingleContextSelf(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getFunction(), 3)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return callTernaryBuiltin(frame, builtinNode, cachedFunc.getSelf(), arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callMethodSelf(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getFunction(), 3)") PythonBuiltinBaseNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return callTernaryBuiltin(frame, builtinNode, func.getSelf(), arg1, arg2);
    }

    @Specialization(guards = "!isBinaryOrTernaryBuiltinDescriptor(func)", //
                    replaces = {"callObjectSingleContext", "callObject", "callMethodSingleContext", "callMethod", "callMethodSingleContextSelf", "callMethodSelf"})
    @Megamorphic
    static Object call(VirtualFrame frame, Object func, Object arg1, Object arg2,
                    @Cached CallNode callNode,
                    @Cached ConditionProfile isBoundProfile) {
        if (isBoundProfile.profile(func instanceof BoundDescriptor)) {
            return callNode.execute(frame, ((BoundDescriptor) func).descriptor, new Object[]{arg2}, PKeyword.EMPTY_KEYWORDS);
        } else {
            return callNode.execute(frame, func, new Object[]{arg1, arg2}, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
