/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageClear;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageEq;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetReverseIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItemWithHash;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltinsFactory.DispatchMissingNodeGen;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDefaultDict})
public final class DictBuiltins extends PythonBuiltins {

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private HashingStorage.InitNode initNode;

        private HashingStorage.InitNode getInitNode() {
            if (initNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initNode = insert(HashingStorage.InitNode.create());
            }
            return initNode;
        }

        @Specialization(guards = {"args.length == 1"})
        Object doVarargs(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, getInitNode().execute(frame, args[0], kwargs), self);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        Object doKeywords(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, getInitNode().execute(frame, NO_VALUE, kwargs), self);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object doEmpty(PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        Object doGeneric(@SuppressWarnings("unused") PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "dict", 1, args.length);
        }
    }

    // setdefault(key[, default])
    @Builtin(name = "setdefault", minNumOfPositionalArgs = 2, parameterNames = {"self", "key", "default"})
    @GenerateNodeFactory
    public abstract static class SetDefaultNode extends PythonTernaryBuiltinNode {
        @Child HashingStorageSetItemWithHash setItemWithHash;

        @Specialization
        public Object doIt(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached PyObjectHashNode hashNode,
                        @Cached HashingStorageGetItemWithHash getItem,
                        @Cached BranchProfile hasValue,
                        @Cached BranchProfile defaultValProfile) {
            long keyHash = hashNode.execute(frame, key);
            Object value = getItem.execute(frame, dict.getDictStorage(), key, keyHash);
            if (value != null) {
                hasValue.enter();
                return value;
            }
            Object newValue = defaultValue;
            if (defaultValue == PNone.NO_VALUE) {
                defaultValProfile.enter();
                newValue = PNone.NONE;
            }
            if (setItemWithHash == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemWithHash = insert(HashingStorageSetItemWithHash.create());
            }
            HashingStorage newStorage = setItemWithHash.execute(frame, dict.getDictStorage(), key, keyHash, newValue);
            dict.setDictStorage(newStorage);
            return newValue;
        }
    }

    // pop(key[, default])
    @Builtin(name = "pop", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isNoValue(defaultValue)")
        public Object popDefault(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached ConditionProfile hasKeyProfile,
                        @Shared("delItem") @Cached HashingStorageDelItem delItem) {
            Object retVal = delItem.executePop(frame, dict.getDictStorage(), key, dict);
            if (hasKeyProfile.profile(retVal != null)) {
                return retVal;
            } else {
                return defaultValue;
            }
        }

        @Specialization(guards = "isNoValue(defaultValue)")
        public Object popWithoutDefault(VirtualFrame frame, PDict dict, Object key, @SuppressWarnings("unused") PNone defaultValue,
                        @Shared("delItem") @Cached HashingStorageDelItem delItem) {
            Object retVal = delItem.executePop(frame, dict.getDictStorage(), key, dict);
            if (retVal != null) {
                return retVal;
            }
            throw raise(PythonBuiltinClassType.KeyError, ErrorMessages.S, key);
        }
    }

    // popitem()
    @Builtin(name = "popitem", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PopItemNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object popItem(VirtualFrame frame, PDict dict,
                        @Cached HashingStorageDelItem delItem,
                        @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue) {
            HashingStorage storage = dict.getDictStorage();
            HashingStorageIterator it = getReverseIterator.execute(storage);
            while (iterNext.execute(storage, it)) {
                Object key = iterKey.execute(storage, it);
                PTuple result = factory().createTuple(new Object[]{key, iterValue.execute(storage, it)});
                delItem.execute(frame, storage, key, dict);
                return result;
            }
            throw raise(KeyError, ErrorMessages.IS_EMPTY, "popitem(): dictionary");
        }
    }

    // keys()
    @Builtin(name = J_KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView keys(PDict self) {
            return factory().createDictKeysView(self);
        }
    }

    // items()
    @Builtin(name = J_ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView items(PDict self) {
            return factory().createDictItemsView(self);
        }
    }

    // get(key[, default])
    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        public static Object doWithDefault(VirtualFrame frame, PDict self, Object key, Object defaultValue,
                        @Cached HashingStorageGetItem getItem) {
            final Object value = getItem.execute(frame, self.getDictStorage(), key);
            return value != null ? value : (defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private DispatchMissingNode missing;

        @Specialization
        Object getItem(VirtualFrame frame, PDict self, Object key,
                        @Cached HashingStorageGetItem getItem) {
            final Object result = getItem.execute(frame, self.getDictStorage(), key);
            if (result == null) {
                if (missing == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    missing = insert(DispatchMissingNodeGen.create());
                }
                return missing.execute(frame, self, key);
            }
            return result;
        }
    }

    @ImportStatic(SpecialMethodSlot.class)
    protected abstract static class DispatchMissingNode extends Node {

        protected abstract Object execute(VirtualFrame frame, Object self, Object key);

        @Specialization
        protected static Object misssing(VirtualFrame frame, Object self, Object key,
                        @Cached("create(Missing)") LookupAndCallBinaryNode callMissing,
                        @Cached DefaultMissingNode defaultMissing) {
            Object result = callMissing.executeObject(frame, self, key);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return defaultMissing.execute(key);
            }
            return result;
        }
    }

    protected abstract static class DefaultMissingNode extends PNodeWithRaise {
        public abstract Object execute(Object key);

        @Specialization
        Object run(TruffleString key) {
            throw raise(KeyError, ErrorMessages.S, key);
        }

        @Fallback
        Object run(Object key) {
            throw raise(KeyError, new Object[]{key});
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PDict self, Object key, Object value,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PDict self, Object key,
                        @Cached HashingStorageDelItem delItem) {
            Object found = delItem.executePop(frame, self.getDictStorage(), key, self);
            if (found != null) {
                return PNone.NONE;
            }
            throw raise(KeyError, ErrorMessages.S, key);
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(@SuppressWarnings("unused") PDict self,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIterator) {
            HashingStorage dictStorage = self.getDictStorage();
            return factory().createDictKeyIterator(getIterator.execute(dictStorage), dictStorage, lenNode.execute(dictStorage));
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(PDict self,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetReverseIterator getReverseIterator) {
            HashingStorage storage = self.getDictStorage();
            return factory().createDictKeyIterator(getReverseIterator.execute(storage), storage, lenNode.execute(storage));
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doDictDict(VirtualFrame frame, PDict self, PDict other,
                        @Cached HashingStorageEq eqNode) {
            return eqNode.execute(frame, self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean run(VirtualFrame frame, PDict self, Object key,
                        @Cached HashingStorageGetItem getItem) {
            return getItem.hasKey(frame, self.getDictStorage(), key);
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static int len(PDict self,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(self.getDictStorage());
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDict dict,
                        @Cached HashingStorageCopy copyNode) {
            return factory().createDict(copyNode.execute(dict.getDictStorage()));
        }
    }

    // clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static PDict clear(PDict dict,
                        @Cached HashingStorageClear clearNode) {
            HashingStorage newStorage = clearNode.execute(dict.getDictStorage());
            dict.setDictStorage(newStorage);
            return dict;
        }
    }

    // values()
    @Builtin(name = J_VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PDictView values(PDict self) {
            return factory().createDictValuesView(self);
        }
    }

    // update()
    @Builtin(name = "update", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class UpdateNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
        static Object updateEmpy(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs) {
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length == 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, args[0]);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 0", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            updateKwargs(frame, self, kwargs, initNode, addAllToOtherNode);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length == 1", "kwargs.length > 0"})
        static Object update(VirtualFrame frame, PDict self, Object[] args, PKeyword[] kwargs,
                        @Shared("updateNode") @Cached DictNodes.UpdateNode updateNode,
                        @Shared("initNode") @Cached HashingStorage.InitNode initNode,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            updateNode.execute(frame, self, args[0]);
            updateKwargs(frame, self, kwargs, initNode, addAllToOtherNode);
            return PNone.NONE;
        }

        @Specialization(guards = "args.length > 1")
        @SuppressWarnings("unused")
        Object error(PDict self, Object[] args, PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, "update", 1, args.length);
        }

        private static void updateKwargs(VirtualFrame frame, PDict self, PKeyword[] kwargs, HashingStorage.InitNode initNode, HashingStorageAddAllToOther addAllToOtherNode) {
            addAllToOtherNode.execute(frame, initNode.execute(frame, PNone.NO_VALUE, kwargs), self);
        }
    }

    // fromkeys()
    @Builtin(name = "fromkeys", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "iterable", "value"}, isClassmethod = true)
    @ImportStatic(SpecialMethodSlot.class)
    @GenerateNodeFactory
    public abstract static class FromKeysNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isBuiltinDict(cls, isSameTypeNode)", limit = "1")
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage s = getHashingStorageNode.execute(frame, iterable, value);
            return factory().createDict(cls, s);
        }

        @Fallback
        public Object doKeys(VirtualFrame frame, Object cls, Object iterable, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callCtor,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "SetItem") LookupSpecialMethodSlotNode lookupSetItem,
                        @Cached CallTernaryMethodNode callSetItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object dict = callCtor.execute(frame, cls);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            Object it = getIter.execute(frame, iterable);
            Object setitemMethod = lookupSetItem.execute(frame, getClassNode.execute(dict), dict);
            if (setitemMethod != PNone.NO_VALUE) {
                while (true) {
                    try {
                        Object key = nextNode.execute(frame, it);
                        callSetItem.execute(frame, setitemMethod, dict, key, val);
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        break;
                    }
                }
                return dict;
            } else {
                throw raise(TypeError, ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, iterable);
            }
        }

        protected static boolean isBuiltinDict(Object cls, TypeNodes.IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(PythonBuiltinClassType.PDict, cls);
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        PDict or(VirtualFrame frame, PDict self, PDict other,
                        @Cached HashingStorageCopy copyNode,
                        @Cached DictNodes.UpdateNode updateNode) {
            PDict merged = factory().createDict(copyNode.execute(self.getDictStorage()));
            updateNode.execute(frame, merged, other);
            return merged;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object or(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___IOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        PDict or(VirtualFrame frame, PDict self, Object other,
                        @Cached DictNodes.UpdateNode updateNode) {
            updateNode.execute(frame, self, other);
            return self;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object classGetItem(Object cls, Object key) {
            return factory().createGenericAlias(cls, key);
        }
    }
}
