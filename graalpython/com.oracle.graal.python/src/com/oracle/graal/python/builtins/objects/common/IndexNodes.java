/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.BoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.NormalizeIndexWithBoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.NormalizeIndexWithoutBoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class IndexNodes {

    public static final class NormalizeIndexNode extends Node {

        @Child private NormalizeIndexCustomMessageNode subNode;

        private final TruffleString errorMessage;

        public NormalizeIndexNode(TruffleString errorMessage, boolean boundsCheck) {
            this.errorMessage = errorMessage;
            if (boundsCheck) {
                subNode = NormalizeIndexWithBoundsCheckNodeGen.create();
            } else {
                subNode = NormalizeIndexWithoutBoundsCheckNodeGen.create();
            }
        }

        public int execute(Object index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(int index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(long index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(boolean index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public long executeLong(long index, long length) {
            return subNode.executeLong(index, length, errorMessage);
        }

        protected final TruffleString getErrorMessage() {
            return errorMessage;
        }

        public static NormalizeIndexNode create() {
            return new NormalizeIndexNode(ErrorMessages.INDEX_OUT_OF_RANGE, true);
        }

        public static NormalizeIndexNode create(TruffleString errorMessage) {
            return new NormalizeIndexNode(errorMessage, true);
        }

        public static NormalizeIndexNode create(boolean boundsCheck) {
            return new NormalizeIndexNode(ErrorMessages.INDEX_OUT_OF_RANGE, boundsCheck);
        }

        public static NormalizeIndexNode create(TruffleString errorMessage, boolean boundsCheck) {
            return new NormalizeIndexNode(errorMessage, boundsCheck);
        }

        public static NormalizeIndexNode forList() {
            return create(ErrorMessages.LIST_INDEX_OUT_OF_RANGE);
        }

        public static NormalizeIndexNode forListAssign() {
            return create(ErrorMessages.LIST_ASSIGMENT_INDEX_OUT_OF_RANGE);
        }

        public static NormalizeIndexNode forTuple() {
            return create(ErrorMessages.TUPLE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTupleAssign() {
            return create(ErrorMessages.TUPLE_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArray() {
            return create(ErrorMessages.ARRAY_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArrayAssign() {
            return create(ErrorMessages.ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forPop() {
            return create(ErrorMessages.POP_INDEX_OUT_OF_RANGE);
        }

        public static NormalizeIndexNode forRange() {
            return create(ErrorMessages.RANGE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forBytearray() {
            return create(ErrorMessages.BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    public abstract static class NormalizeIndexCustomMessageNode extends Node {
        public abstract int execute(Object index, int length, TruffleString errorMessage);

        public abstract int execute(boolean index, int length, TruffleString errorMessage);

        public abstract int execute(long index, int length, TruffleString errorMessage);

        public abstract int execute(int index, int length, TruffleString errorMessage);

        public abstract long executeLong(long index, long length, TruffleString errorMessage);

        public static NormalizeIndexCustomMessageNode create() {
            return NormalizeIndexWithBoundsCheckNodeGen.create();
        }

        public static NormalizeIndexCustomMessageNode getUncached() {
            return NormalizeIndexWithBoundsCheckNodeGen.getUncached();
        }

        public static NormalizeIndexCustomMessageNode createWithoutBoundsCheck() {
            return NormalizeIndexWithoutBoundsCheckNodeGen.create();
        }

        public static NormalizeIndexCustomMessageNode getUncachedWithoutBoundsCheck() {
            return NormalizeIndexWithoutBoundsCheckNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class NormalizeIndexWithBoundsCheckNode extends NormalizeIndexCustomMessageNode {

        @Specialization
        static int doInt(int index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int normalizedIndex = index;
            if (negativeIndexProfile.profile(normalizedIndex < 0)) {
                normalizedIndex += length;
            }
            boundsCheckNode.execute(errorMessage, normalizedIndex, length);
            return normalizedIndex;
        }

        @Specialization
        static int doBool(boolean bIndex, int length, TruffleString errorMessage,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int index = PInt.intValue(bIndex);
            boundsCheckNode.execute(errorMessage, index, length);
            return index;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doLong(long lIndex, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) throws OverflowException {
            int index = PInt.intValueExact(lIndex);
            return doInt(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doLong(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(PythonBuiltinClassType.IndexError, index);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doPInt(PInt index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) throws OverflowException {
            int idx = index.intValueExact();
            return doInt(idx, length, errorMessage, negativeIndexProfile, boundsCheckNode);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doPInt(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(PythonBuiltinClassType.IndexError, index);
            }
        }

        @Specialization
        static long doLongLong(long lIndex, long length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            long normalizedIndex = lIndex;
            if (negativeIndexProfile.profile(normalizedIndex < 0)) {
                normalizedIndex += length;
            }
            boundsCheckNode.execute(errorMessage, normalizedIndex, length);
            return normalizedIndex;
        }

    }

    @GenerateUncached
    abstract static class NormalizeIndexWithoutBoundsCheckNode extends NormalizeIndexCustomMessageNode {

        @Specialization
        static int doInt(int index, int length, @SuppressWarnings("unused") TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            return idx;
        }

        @Specialization
        static int doBool(boolean index, @SuppressWarnings("unused") int length, @SuppressWarnings("unused") TruffleString errorMessage) {
            return PInt.intValue(index);
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doLong(long index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile) throws OverflowException {
            int idx = PInt.intValueExact(index);
            return doInt(idx, length, errorMessage, negativeIndexProfile);
        }

        @Specialization(replaces = "doLong")
        static int doLongOvf(long index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doLong(index, length, errorMessage, negativeIndexProfile);
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(PythonBuiltinClassType.IndexError, index);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doPInt(PInt index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile) throws OverflowException {
            int idx = index.intValueExact();
            return doInt(idx, length, errorMessage, negativeIndexProfile);
        }

        @Specialization(replaces = "doPInt")
        static int doPIntOvf(PInt index, int length, TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doPInt(index, length, errorMessage, negativeIndexProfile);
            } catch (OverflowException e) {
                throw raiseNode.raiseNumberTooLarge(PythonBuiltinClassType.IndexError, index);
            }
        }

        @Specialization
        static long doLongLong(long index, long length, @SuppressWarnings("unused") TruffleString errorMessage,
                        @Shared("negativeIndexProfile") @Cached ConditionProfile negativeIndexProfile) {
            long idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            return idx;
        }
    }

    @GenerateUncached
    public abstract static class BoundsCheckNode extends Node {

        public abstract void execute(TruffleString errorMessage, int idx, int length);

        public abstract void execute(TruffleString errorMessage, long idx, long length);

        @Specialization
        static void doBoundsCheck(TruffleString errorMessage, int idx, int length,
                        @Cached ConditionProfile outOfBoundsProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                throw raiseNode.raise(PythonBuiltinClassType.IndexError, errorMessage);
            }
        }

        @Specialization
        static void doBoundsCheck(TruffleString errorMessage, long idx, long length,
                        @Cached ConditionProfile outOfBoundsProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                throw raiseNode.raise(PythonBuiltinClassType.IndexError, errorMessage);
            }
        }

        public static BoundsCheckNode create() {
            return BoundsCheckNodeGen.create();
        }

        public static BoundsCheckNode getUncached() {
            return BoundsCheckNodeGen.getUncached();
        }
    }

}
