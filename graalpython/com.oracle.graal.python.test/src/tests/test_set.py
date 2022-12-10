# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# ankitv 10/10/13
# Iterating by Sequence Index

from collections.abc import MutableSet

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


class PassThru(Exception):
    pass


def check_pass_thru():
    raise PassThru
    yield 1


def test_set_or_union():
    s1 = {1, 2, 3}
    s2 = {4, 5, 6}
    s3 = {1, 2, 4}
    s4 = {1, 2, 3}
    
    sstr1 = {'a', 'b', 'c'}
    sstr2 = {'d', 'e', 'f'}
    sstr3 = {'a', 'b', 'd'}
    sstr4 = {'a', 'b', 'c'}
    
    or_result = s1 | s2
    union_result = s1.union(s2)
    assert or_result == {1, 2, 3, 4, 5, 6}
    assert union_result == {1, 2, 3, 4, 5, 6}
    
    or_result = s2 | s1
    union_result = s2.union(s1)
    assert or_result == {1, 2, 3, 4, 5, 6}
    assert union_result == {1, 2, 3, 4, 5, 6}

    or_result = sstr1 | sstr2
    union_result = sstr1.union(sstr2)
    assert or_result == {'a', 'b', 'c', 'd', 'e', 'f'}
    assert union_result == {'a', 'b', 'c', 'd', 'e', 'f'}

    or_result = s1 | sstr2
    union_result = s1.union(sstr2)
    assert or_result == {1, 2, 3, 'd', 'e', 'f'}
    assert union_result == {1, 2, 3, 'd', 'e', 'f'}
    
    or_result = sstr1 | s1
    union_result = sstr1.union(s1)
    assert or_result == {1, 2, 3, 'a', 'b', 'c'}
    assert union_result == {1, 2, 3, 'a', 'b', 'c'}
    
    or_result = s1 | s3
    union_result = s1.union(s3)
    assert or_result == {1, 2, 3, 4}
    assert union_result == {1, 2, 3, 4}
    
    or_result = s3 | s1
    union_result = s3.union(s1)
    assert or_result == {1, 2, 3, 4}
    assert union_result == {1, 2, 3, 4}
    
    or_result = sstr1 | sstr3
    union_result = sstr1.union(sstr3)
    assert or_result == {'a', 'b', 'c', 'd'}
    assert union_result == {'a', 'b', 'c', 'd'}
    
    or_result = sstr3 | sstr1
    union_result = sstr3.union(sstr1)
    assert or_result == {'a', 'b', 'c', 'd'}
    assert union_result == {'a', 'b', 'c', 'd'}

    or_result = s1 | sstr3
    union_result = s1.union(sstr3)
    assert or_result == {1, 2, 3, 'a', 'b', 'd'}
    assert union_result == {1, 2, 3, 'a', 'b', 'd'}

    or_result = sstr1 | s3
    union_result = sstr1.union(s3)
    assert or_result == {1, 2, 4, 'a', 'b', 'c'}
    assert union_result == {1, 2, 4, 'a', 'b', 'c'}
    
    or_result = s1 | s4
    union_result = s1.union(s4)
    assert or_result == {1, 2, 3}
    assert union_result == {1, 2, 3}
    
    or_result = s4 | s1
    union_result = s4.union(s1)
    assert or_result == {1, 2, 3}
    assert union_result == {1, 2, 3}
    
    or_result = sstr1 | sstr4
    union_result = sstr1.union(sstr4)
    assert or_result == {'a','b','c'}
    assert union_result == {'a','b','c'}
    
    or_result = sstr4 | sstr1
    union_result = sstr4.union(sstr1)
    assert or_result == {'a','b','c'}
    assert union_result == {'a','b','c'}

    assert frozenset((1,2)) | {1:2}.items() == {1, 2, (1, 2)}
    assert frozenset((1,2)) | {1:2}.keys() == {1, 2}
    
    assert frozenset(('a','b')) | {1:2}.keys() == {'a', 'b', 1}
    assert frozenset(('a','b')) | {1:2, 3:4}.keys() == {'a', 'b', 1, 3}
    assert frozenset((1,2)) | {'a':2, 'b':4}.keys() == {'a', 'b', 1, 2}
    
    assert {1,2} | {3:4, 5:6}.keys() == {1, 2, 3, 5}
    assert {3:4, 5:6}.keys() | {1,2} == {1, 2, 3, 5}
    assert {1,2} | {'a':1, 'b':2}.keys() == {1, 2, 'a', 'b'}
    assert {'a','b'} | {1:'c', 2:'d'}.keys() == {1, 2, 'a', 'b'}

def test_set_and():
    assert frozenset((1,2)) & {1:2}.items() == set()
    assert frozenset((1,2)) & {1:2}.keys() == {1}

def test_set_union():
    assert {1, 2, 3}.union({1: 'a', 2: 'b', 4: 'd'}) == {1, 2, 3, 4}
    assert {1, 2, 3}.union([2, 3, 4, 5]) == {1, 2, 3, 4, 5}
    assert {1, 2, 3}.union((3, 4, 5, 6)) == {1, 2, 3, 4, 5, 6}


def test_set_remove():
    s = {1, 2, 3}
    assert s == {1, 2, 3}
    s.remove(3)
    assert s == {1, 2}


def test_set_le():
    assert set("a") <= set("abc")


def test_difference():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)

    i = s.difference(otherword)
    for c in letters:
        assert (c in i) == (c in d and c not in otherword)

    assert s == set(word)
    assert type(i) == set
    assert_raises(PassThru, s.difference, check_pass_thru())
    assert_raises(TypeError, s.difference, [[]])

    for C in set, frozenset, dict.fromkeys, str, list, tuple:
        assert set('abcba').difference(C('cdc')) == set('ab')
        assert set('abcba').difference(C('efgfe')) == set('abc')
        assert set('abcba').difference(C('ccb')) == set('a')
        assert set('abcba').difference(C('ef')) == set('abc')
        assert set('abcba').difference() == set('abc')
        assert set('abcba').difference(C('a'), C('b')) == set('c')


def test_difference_update():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)


    retval = s.difference_update(otherword)
    assert retval == None

    for c in (word + otherword):
        if c in word and c not in otherword:
            assert c in s
        else:
            assert c not in s

    assert_raises(PassThru, s.difference_update, check_pass_thru())
    assert_raises(TypeError, s.difference_update, [[]])
    # assert_raises(TypeError, s.symmetric_difference_update, [[]])

    for p, q in (('cdc', 'ab'), ('efgfe', 'abc'), ('ccb', 'a'), ('ef', 'abc')):
        for C in set, frozenset, dict.fromkeys, str, list, tuple:
            s = set('abcba')
            assert s.difference_update(C(p)) == None
            assert s == set(q)

            s = set('abcdefghih')
            s.difference_update()
            assert s == set('abcdefghih')

            s = set('abcdefghih')
            s.difference_update(C('aba'))
            assert s == set('cdefghih')

            s = set('abcdefghih')
            s.difference_update(C('cdc'), C('aba'))
            assert s == set('efghih')


def test_sub_and_super():
    for thetype in [set, frozenset]:
        p, q, r = map(thetype, ['ab', 'abcde', 'def'])
        assert p < q
        assert p <= q
        assert q <= q
        assert q > p
        assert q >= p
        assert not q < r
        assert not q <= r
        assert not q > r
        assert not q >= r
        assert set('a').issubset('abc')
        assert set('abc').issuperset('a')
        assert not set('a').issubset('cbs')
        assert not set('cbs').issuperset('a')


def test_superset_subset():
    l = [1, 2, 3, 4]
    s = set(l)
    t = tuple(l)
    assert s.issuperset(s)
    assert s.issuperset(l)
    assert s.issuperset(t)
    assert s >= s
    assert_raises(TypeError, lambda: s >= l)
    assert_raises(TypeError, lambda: s >= t)

    assert s.issubset(s)
    assert s.issubset(l)
    assert s.issubset(t)
    assert s <= s
    assert_raises(TypeError, lambda: s <= l)
    assert_raises(TypeError, lambda: s <= t)


def test_intersection():
    word = 'simsalabim'
    otherword = 'madagascar'
    letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
    s = set(word)
    d = dict.fromkeys(word)

    i = s.intersection(otherword)
    for c in letters:
        assert (c in i) == (c in d and c in otherword)

    assert s == set(word)
    # assert type(i) == set
    assert_raises(PassThru, s.intersection, check_pass_thru())

    for C in set, frozenset, dict.fromkeys, str, list, tuple:
        assert set('abcba').intersection(C('cdc')) == set('cc')
        assert set('abcba').intersection(C('efgfe')) == set('')
        assert set('abcba').intersection(C('ccb')) == set('bc')
        assert set('abcba').intersection(C('ef')) == set('')
        assert set('abcba').intersection(C('cbcf'), C('bag')) == set('b')

    # TODO: currently the id function behaves a bit differently than the one in cPython
    # s = set('abcba')
    # z = s.intersection()
    # if set == frozenset():
    #     assert id(s) == id(z)
    # else:
    #     assert id(s) != id(z)


def test_same_id():
    empty_ids = set([id(frozenset()) for i in range(100)])
    assert len(empty_ids) == 1

def test_init():
    s = {1, 2, 3}
    s.__init__({4})
    assert s == {4}
    s.__init__()
    assert s == set()

def test_rich_compare():
    class TestRichSetCompare:
        def __gt__(self, some_set):
            self.gt_called = True
            return False
        def __lt__(self, some_set):
            self.lt_called = True
            return False
        def __ge__(self, some_set):
            self.ge_called = True
            return False
        def __le__(self, some_set):
            self.le_called = True
            return False

    # This first tries the builtin rich set comparison, which doesn't know
    # how to handle the custom object. Upon returning NotImplemented, the
    # corresponding comparison on the right object is invoked.
    myset = {1, 2, 3}

    myobj = TestRichSetCompare()
    myset < myobj
    assert myobj.gt_called

    myobj = TestRichSetCompare()
    myset > myobj
    assert myobj.lt_called

    myobj = TestRichSetCompare()
    myset <= myobj
    assert myobj.ge_called

    myobj = TestRichSetCompare()
    myset >= myobj
    assert myobj.le_called


def test_pop():
    s = {1, 2, 3}
    l = []
    l.append(s.pop())
    l.append(s.pop())
    l.append(s.pop())
    assert set(l) == {1, 2, 3}
    try:
        s.pop()
    except BaseException as e:
        assert type(e) == KeyError, "expected KeyError, got %s" % type(e)


def test_set_delete():
    s = {1, 2, 3}
    assert s == {1, 2, 3}
    s.discard(3)
    assert s == {1, 2}

    # string keys
    s = {'a', 'b', 'c'}
    assert s == {'a', 'b', 'c'}
    s.discard('c')
    assert s == {'a', 'b'}


def test_literal():
    d = {"a": 1, "b": 2, "c": 3}
    e = {"uff": "foo"}
    assert {*d, *e} == {"a", "b", "c", "uff"}

    d = {}
    assert {*d} == set()


def test_hashable_frozenset():
    seq = list(range(10)) + list('abcdefg') + ['apple']
    key1 = frozenset(seq)
    key2 = frozenset(reversed(seq))
    assert key1 == key2
    assert not (id(key1) == id(key2))
    d = {key1: 42}
    assert hash(key1) == hash(key2)
    assert d[key2] == 42


def test_equality():
    s1 = {1, 2, 3}
    s2 = {1, 3}
    assert not s1 == s2
    assert not s2 == s1


def test_isdisjoint():
    x = {1, 2, 3}
    y = set()
    assert not x.isdisjoint(x)
    assert y.isdisjoint(y)
    assert {1, 2, 3}.isdisjoint({4, 5})
    assert {4, 5}.isdisjoint({1, 2, 3})
    assert not {1, 2, 3}.isdisjoint({4, 5, 1, 8})
    assert not {1, 2, 3, 4, 5, 6}.isdisjoint({4, 5})
    assert set().isdisjoint({4, 5})
    assert {4, 5}.isdisjoint(set())


def test_isdisjoint_customobjs():
    class MyIter:
        def __init__(self, length):
            self.index = length

        def __next__(self):
            if self.index == 0:
                raise StopIteration
            self.index -= 1
            return self.index

    class MySetWithIter(frozenset):
        def __init__(self, forward):
            frozenset.__init__(forward)

        def __iter__(self):
            return MyIter(4)

        def __len__(self):
            return 4

    # iterates over the custom set
    assert {10, 11}.isdisjoint(MySetWithIter({}))
    assert not {1, 2}.isdisjoint(MySetWithIter({}))

    # for 'self' we check the actual set elements, not what iterator gives
    assert MySetWithIter({}).isdisjoint({1,2})
    assert not MySetWithIter({1}).isdisjoint({1,2})
    assert MySetWithIter({10, 11, 12, 13}).isdisjoint({1,2})

    # with non-set object
    class NonSetWithIter:
        def __iter__(self):
            return MyIter(4)

        def __len__(self):
            return 4

    assert {10, 11}.isdisjoint(NonSetWithIter())
    assert {10, 11, 12, 13, 14}.isdisjoint(NonSetWithIter())
    assert not {1, 2}.isdisjoint(NonSetWithIter())
    assert not {1, 2, 3, 4, 5}.isdisjoint(NonSetWithIter())

def test_iter_changed_size():
    def just_iterate(it):
        for i in it:
            pass

    def iterate_and_update(it):
        for i in it:
            s.add(3)

    s = {1, 2}
    it = iter(s)
    s.pop()
    assert_raises(RuntimeError, just_iterate, it)

    s = {1, 2}
    assert_raises(RuntimeError, iterate_and_update, s)

# copied and modified test_collections.py#test_issue_4920
# the original will always fail on graalpython due to different set order
def test_MutableSet_pop():
    class MySet(MutableSet):
        __slots__=['__s']
        def __init__(self,items=None):
            if items is None:
                items=[]
            self.__s=set(items)
        def __contains__(self,v):
            return v in self.__s
        def __iter__(self):
            return iter(self.__s)
        def __len__(self):
            return len(self.__s)
        def add(self,v):
            result=v not in self.__s
            self.__s.add(v)
            return result
        def discard(self,v):
            result=v in self.__s
            self.__s.discard(v)
            return result
        def __repr__(self):
            return "MySet(%s)" % repr(list(self))
    l = [5,43]
    s = MySet(l)
    v1 = s.pop()
    assert v1 in l
    v2 = s.pop()
    assert v2 in l
    assert v1 != v2
    assert len(s) == 0

def test_inplace_ops_mutate():
    for op in ('-', '&', '|', '^'):
        s1 = {1, 2}
        s2 = {1, 3}
        v = {'a': s1, 'b': s2}
        exec(f"a {op}= b", v)
        assert v['a'] is s1
        assert s1 == eval(f"a {op} b", {'a': {1, 2}, 'b': {1, 3}})


def test_graal_4816():
    from copy import copy

    def do_something(numbers):
        assert len(numbers)

    foo = set([1, 2, 3, 4, 5, 6, 7, 8, 9])

    for _ in copy(foo):
        foo.pop()
        if foo:
            do_something(foo)

class TrackingKey:
    def __init__(self, id, hash=None):
        self.clear_observations()
        self.hash = hash
        self.id = id
    def __hash__(self):
        self.hash_calls += 1
        return self.hash if self.hash else hash(self.id)
    def __eq__(self, other):
        self.eq_calls += 1
        return self.id == getattr(other, 'id', other)
    def clear_observations(self):
        self.hash_calls = 0
        self.eq_calls = 0


def test_update_side_effects():
    key1 = TrackingKey('foo', hash=42)
    key2 = TrackingKey('bar', hash=42)
    d1 = {key1}
    l = [key2]
    key1.clear_observations()
    key2.clear_observations()
    d1.update(l)
    assert key1.eq_calls == 1
    assert key1.hash_calls == 0
    assert key2.eq_calls == 0
    assert key2.hash_calls == 1


def test_bin_ops_side_effects():
    def test_op(op, check):
        key1 = TrackingKey('foo', hash=42)
        key2 = TrackingKey('bar', hash=42)
        d1 = {key1}
        d2 = {key2}
        key1.clear_observations()
        key2.clear_observations()
        op(d1, d2)
        check(key1, key2)

    def key1_eq_call(key1, key2):
        assert key1.eq_calls == 1
        assert key1.hash_calls == 0
        assert key2.eq_calls == 0
        assert key2.hash_calls == 0

    import operator
    test_op(operator.__or__, key1_eq_call)
    test_op(operator.__ior__, key1_eq_call)
    test_op(operator.__and__, key1_eq_call)
    test_op(operator.__iand__, key1_eq_call)

    # TODO: GR-42240
    #
    # def symmetric_difference_check(key1, key2):
    #     assert key1.eq_calls == 0
    #     assert key1.hash_calls == 0
    #     assert key2.eq_calls == 2
    #     assert key2.hash_calls == 0
    #
    # test_op(set.symmetric_difference, symmetric_difference_check)
    # test_op(operator.__xor__, symmetric_difference_check)
    # test_op(operator.__ixor__, symmetric_difference_check)
    #
    # def symmetric_difference_update_check(key1, key2):
    #     assert key1.eq_calls == 2
    #     assert key1.hash_calls == 0
    #     assert key2.eq_calls == 0
    #     assert key2.hash_calls == 0
    #
    # test_op(set.symmetric_difference_update, symmetric_difference_update_check)
    #
    # TODO: intersection, intersection_update

# GR-41996
# def test_pop_side_effects():
#     class TrackingKey:
#         def __init__(self):
#             self.hash_calls = 0
#             self.eq_calls = 0
#         def __hash__(self):
#             self.hash_calls += 1
#             return 42
#         def __eq__(self, other):
#             self.eq_calls += 1
#             return True
#
#     key = TrackingKey()
#     s = {key, 'other_key'}
#     assert key.hash_calls == 1
#     assert key.eq_calls == 0, "first"
#
#     val = s.pop()
#     assert key.hash_calls == 1
#     assert key.eq_calls == 0
#     assert val == key


def test_set_iterator_reduce():
    s = {1, 2, 3}
    it = s.__iter__()
    it.__reduce__()
    assert [i for i in it] == [1, 2, 3]
