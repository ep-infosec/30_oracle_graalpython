/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.BuiltinNames.J_ADD;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetHashingStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageClear;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDiff;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIntersect;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageXor;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * binary operations are implemented in {@link BaseSetBuiltins}
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.PSet)
public final class SetBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SetBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class InitNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(iterable)")
        static PNone doNoValue(PSet self, @SuppressWarnings("unused") PNone iterable,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(self.getDictStorage());
            self.setDictStorage(newStorage);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(iterable)"})
        static PNone doGeneric(VirtualFrame frame, PSet self, Object iterable,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage storage = getHashingStorageNode.doNoValue(frame, iterable);
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        @Fallback
        PNone fail(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object self, Object iterable) {
            throw raise(TypeError, ErrorMessages.SET_DOES_NOT_SUPPORT_ITERABLE_OBJ, iterable);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {

        @Specialization
        PSet doSet(PSet self,
                        @Cached HashingStorageCopy copyNode) {
            return factory().createSet(copyNode.execute(self.getDictStorage()));
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static Object clear(PSet self,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(self.getDictStorage());
            self.setDictStorage(newStorage);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ADD, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static Object add(VirtualFrame frame, PSet self, Object o,
                        @Cached SetNodes.AddNode addNode) {
            addNode.execute(frame, self, o);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageCopy copyStorage,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            // Note: we cannot reuse 'otherStorage' because we need to add from other -> self, in
            // order to execute __eq__ of keys in 'self' and not other
            HashingStorage otherStorage = getHashingStorage.execute(frame, other);
            HashingStorage resultStorage = copyStorage.execute(self.getDictStorage());
            PSet result = factory().createSet(resultStorage);
            addAllToOther.execute(frame, otherStorage, result);
            return result;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            addAllToOther.execute(frame, getHashingStorageNode.execute(frame, other), self);
            return self;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = addAllToOther.execute(frame, getHashingStorageNode.execute(frame, args[i]), result);
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = addAllToOther.execute(frame, getHashingStorageNode.execute(frame, args[i]), result);
            }
            return factory().createSet(result);
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    @GenerateUncached
    public abstract static class UpdateSingleNode extends Node {

        public abstract void execute(VirtualFrame frame, PHashingCollection collection, Object other);

        @Specialization
        static void update(VirtualFrame frame, PHashingCollection collection, PHashingCollection other,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage dictStorage = other.getDictStorage();
            addAllToOther.execute(frame, dictStorage, collection);
        }

        @Specialization
        static void update(VirtualFrame frame, PHashingCollection collection, PDictView.PDictKeysView other,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage dictStorage = other.getWrappedDict().getDictStorage();
            addAllToOther.execute(frame, dictStorage, collection);
        }

        static boolean isBuiltinSequence(Object other, GetClassNode getClassNode) {
            return other instanceof PSequence && !(other instanceof PString) && getClassNode.execute((PSequence) other) instanceof PythonBuiltinClassType;
        }

        @Specialization(guards = "isBuiltinSequence(other, getClassNode)", limit = "1")
        static void doBuiltin(VirtualFrame frame, PHashingCollection collection, @SuppressWarnings("unused") PSequence other,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached HashingStorageSetItem setStorageItem) {
            SequenceStorage sequenceStorage = getSequenceStorageNode.execute(other);
            int length = sequenceStorage.length();
            HashingStorage curStorage = collection.getDictStorage();
            for (int i = 0; i < length; i++) {
                Object key = getItemScalarNode.execute(sequenceStorage, i);
                curStorage = setStorageItem.execute(frame, curStorage, key, PNone.NONE);
            }
            collection.setDictStorage(curStorage);
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isBuiltinSequence(other, getClassNode)"}, limit = "1")
        static void doIterable(VirtualFrame frame, PHashingCollection collection, Object other,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached HashingStorageSetItem setStorageItem) {
            HashingStorage curStorage = collection.getDictStorage();
            Object iterator = getIter.execute(frame, other);
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    collection.setDictStorage(curStorage);
                    return;
                }
                curStorage = setStorageItem.execute(frame, curStorage, key, PNone.NONE);
            }
        }

        public static UpdateSingleNode create() {
            return SetBuiltinsFactory.UpdateSingleNodeGen.create();
        }

        public static UpdateSingleNode getUncached() {
            return SetBuiltinsFactory.UpdateSingleNodeGen.getUncached();
        }
    }

    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length == 1")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached UpdateSingleNode update) {
            update.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage storage = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                storage = addAllToOther.execute(frame, getHashingStorageNode.execute(frame, args[i]), storage);
            }
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            HashingStorage storage = self.getDictStorage();
            for (Object o : args) {
                storage = addAllToOther.execute(frame, getHashingStorageNode.execute(frame, o), storage);
            }
            self.setDictStorage(storage);
            return PNone.NONE;
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            addAllToOther.execute(frame, getHashingStorageNode.execute(frame, other), self);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(right)")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageIntersect intersectNode) {
            HashingStorage storage = intersectNode.execute(frame, getHashingStorageNode.execute(frame, right), left.getDictStorage());
            return factory().createSet(storage);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doAnd(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IAND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IAndNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(right)")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageIntersect intersectNode) {
            // We cannot reuse the left storage without breaking the CPython "contract" of how many
            // times we can call __eq__ on which key
            HashingStorage storage = intersectNode.execute(frame, getHashingStorageNode.execute(frame, right), left.getDictStorage());
            left.setDictStorage(storage);
            return left;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doAnd(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "intersection", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class IntersectNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        Object doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other,
                        @Cached HashingStorageCopy copyNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            return createResult(self, result);
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        Object doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageIntersect intersectNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = intersectNode.execute(frame, result, getHashingStorageNode.execute(frame, args[i]));
            }
            return createResult(self, result);
        }

        @Specialization(replaces = "doCached")
        Object doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageIntersect intersectNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = intersectNode.execute(frame, result, getHashingStorageNode.execute(frame, args[i]));
            }
            return createResult(self, result);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageIntersect intersectNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            result = intersectNode.execute(frame, result, getHashingStorageNode.execute(frame, other));
            return createResult(self, result);
        }

        protected Object createResult(PSet self, HashingStorage result) {
            return factory().createSet(result);
        }
    }

    @Builtin(name = "intersection_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    @ImportStatic({SpecialMethodNames.class})
    public abstract static class IntersectUpdateNode extends IntersectNode {
        @Override
        protected Object createResult(PSet self, HashingStorage result) {
            // In order to be compatible w.r.t. __eq__ calls we cannot reuse self storage
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageXor xorNode) {
            // TODO: calls __eq__ wrong number of times compared to CPython (GR-42240)
            return factory().createSet(xorNode.execute(frame, self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IXOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IXorNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "canDoSetBinOp(other)")
        Object doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageXor xorNode) {
            self.setDictStorage(xorNode.execute(frame, self.getDictStorage(), getHashingStorageNode.execute(frame, other)));
            return self;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doOr(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "symmetric_difference", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceNode extends PythonBuiltinNode {

        @Specialization
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageXor xorNode) {
            HashingStorage result = xorNode.execute(frame, self.getDictStorage(), getHashingStorage.execute(frame, other));
            return factory().createSet(result);
        }
    }

    @Builtin(name = "symmetric_difference_update", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class SymmetricDifferenceUpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doSet(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageXor xorNode) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = xorNode.execute(frame, result, getHashingStorage.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSetArgs(VirtualFrame frame, PSet self, Object[] args,
                        @Shared("getStorage") @Cached GetHashingStorageNode getHashingStorage,
                        @Shared("xor") @Cached HashingStorageXor xorNode) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = xorNode.execute(frame, result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        static PNone doSetOther(VirtualFrame frame, PSet self, Object other,
                        @Shared("getStorage") @Cached GetHashingStorageNode getHashingStorage,
                        @Shared("xor") @Cached HashingStorageXor xorNode) {
            HashingStorage result = xorNode.execute(frame, self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(right)")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage storage = diffNode.execute(frame, left.getDictStorage(), getHashingStorageNode.execute(frame, right));
            return factory().createSet(storage);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doSub(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___ISUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ISubNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "canDoSetBinOp(right)")
        PBaseSet doPBaseSet(VirtualFrame frame, PSet left, Object right,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage storage = diffNode.execute(frame, left.getDictStorage(), getHashingStorageNode.execute(frame, right));
            left.setDictStorage(storage);
            return left;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doSub(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "difference", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DifferenceNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        PSet doSet(@SuppressWarnings("unused") VirtualFrame frame, PSet self, @SuppressWarnings("unused") PNone other) {
            return factory().createSet(self.getDictStorage());
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < len; i++) {
                result = diffNode.execute(frame, result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorageNode,
                        @Cached HashingStorageCopy copyNode,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = copyNode.execute(self.getDictStorage());
            for (int i = 0; i < args.length; i++) {
                result = diffNode.execute(frame, result, getHashingStorageNode.execute(frame, args[i]));
            }
            return factory().createSet(result);
        }

        static boolean isOther(Object arg) {
            return !(PGuards.isNoValue(arg) || arg instanceof Object[]);
        }

        @Specialization(guards = "isOther(other)")
        PSet doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = diffNode.execute(frame, self.getDictStorage(), getHashingStorage.execute(frame, other));
            return factory().createSet(result);
        }
    }

    @Builtin(name = "difference_update", minNumOfPositionalArgs = 1, takesVarArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class DifferenceUpdateNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(other)")
        @SuppressWarnings("unused")
        static PNone doNone(VirtualFrame frame, PSet self, PNone other) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        static PNone doCached(VirtualFrame frame, PSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = self.getDictStorage();
            for (int i = 0; i < len; i++) {
                result = diffNode.execute(frame, result, getHashingStorage.execute(frame, args[i]));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization(replaces = "doCached")
        static PNone doSet(VirtualFrame frame, PSet self, Object[] args,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = self.getDictStorage();
            for (Object o : args) {
                result = diffNode.execute(frame, result, getHashingStorage.execute(frame, o));
            }
            self.setDictStorage(result);
            return PNone.NONE;
        }

        @Specialization
        static PNone doSet(VirtualFrame frame, PSet self, Object other,
                        @Cached GetHashingStorageNode getHashingStorage,
                        @Cached HashingStorageDiff diffNode) {
            HashingStorage result = diffNode.execute(frame, self.getDictStorage(), getHashingStorage.execute(frame, other));
            self.setDictStorage(result);
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PSet self, Object key,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode discardNode) {
            if (!discardNode.execute(frame, self, key)) {
                throw raise(PythonErrorType.KeyError, new Object[]{key});
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "discard", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DiscardNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object discard(VirtualFrame frame, PSet self, Object key,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode discardNode) {
            discardNode.execute(frame, self, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PSet self,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageDelItem delItem) {
            HashingStorage storage = self.getDictStorage();
            HashingStorageIterator it = getIter.execute(storage);
            if (iterNext.execute(storage, it)) {
                Object key = iterKey.execute(storage, it);
                // TODO: (GR-41996) this may still invokes __hash__, may invoke __eq__ and is
                // suboptimal. There is ignored test for this in test_set.py
                delItem.execute(frame, storage, key, self);
                return key;
            }
            throw raise(PythonErrorType.KeyError, ErrorMessages.POP_FROM_EMPTY_SET);
        }
    }

}
