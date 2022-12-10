# Copyright (c) 2019, 2022, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

import unittest, sys
from itertools import *


class CombinationsTests(unittest.TestCase):

    def test_combinations_with_replacement(self):
        cwr = combinations_with_replacement
        self.assertRaises(TypeError, cwr, 'abc')       # missing r argument
        self.assertRaises(TypeError, cwr, 'abc', 2, 1) # too many arguments
        self.assertRaises(TypeError, cwr, None)        # pool is not iterable
        self.assertRaises(ValueError, cwr, 'abc', -2)  # r is negative

        result = list()
        for a in cwr('ABC', 2):
            result += a
        correct = [('A','A'), ('A','B'), ('A','C'), ('B','B'), ('B','C'), ('C','C')]
        compare = list();
        for a in correct:
            compare += a;
        self.assertEqual(result,compare)


    def test_groupby(self):
        # Check whether it accepts arguments correctly
        self.assertEqual([], list(groupby([])))
        self.assertEqual([], list(groupby([], key=id)))
        self.assertRaises(TypeError, list, groupby('abc', []))
        self.assertRaises(TypeError, groupby, None)
        self.assertRaises(TypeError, groupby, 'abc', lambda x:x, 10)

        # Check normal input
        s = [(0, 10, 20), (0, 11,21), (0,12,21), (1,13,21), (1,14,22),
             (2,15,22), (3,16,23), (3,17,23)]
        dup = []
        for k, g in groupby(s, lambda r:r[0]):
            for elem in g:
                self.assertEqual(k, elem[0])
                dup.append(elem)
        self.assertEqual(s, dup)

        # Exercise pipes and filters style
        s = 'abracadabra'
        # sort s | uniq
        r = [k for k, g in groupby(sorted(s))]
        self.assertEqual(r, ['a', 'b', 'c', 'd', 'r'])
        # sort s | uniq -d
        r = [k for k, g in groupby(sorted(s)) if list(islice(g,1,2))]
        self.assertEqual(r, ['a', 'b', 'r'])
        # sort s | uniq -c
        r = [(len(list(g)), k) for k, g in groupby(sorted(s))]
        self.assertEqual(r, [(5, 'a'), (2, 'b'), (1, 'c'), (1, 'd'), (2, 'r')])
        # sort s | uniq -c | sort -rn | head -3
        r = sorted([(len(list(g)) , k) for k, g in groupby(sorted(s))], reverse=True)[:3]
        self.assertEqual(r, [(5, 'a'), (2, 'r'), (2, 'b')])

        # iter.__next__ failure
        class ExpectedError(Exception):
            pass
        def delayed_raise(n=0):
            for i in range(n):
                yield 'yo'
            raise ExpectedError
        def gulp(iterable, keyp=None, func=list):
            return [func(g) for k, g in groupby(iterable, keyp)]

        # iter.__next__ failure on outer object
        self.assertRaises(ExpectedError, gulp, delayed_raise(0))
        # iter.__next__ failure on inner object
        self.assertRaises(ExpectedError, gulp, delayed_raise(1))

        # __eq__ failure
        class DummyCmp:
            def __eq__(self, dst):
                raise ExpectedError
        s = [DummyCmp(), DummyCmp(), None]

        # __eq__ failure on outer object
        self.assertRaises(ExpectedError, gulp, s, func=id)
        # __eq__ failure on inner object
        self.assertRaises(ExpectedError, gulp, s)

        # keyfunc failure
        def keyfunc(obj):
            if keyfunc.skip > 0:
                keyfunc.skip -= 1
                return obj
            else:
                raise ExpectedError

        # keyfunc failure on outer object
        keyfunc.skip = 0
        self.assertRaises(ExpectedError, gulp, [None], keyfunc)
        keyfunc.skip = 1
        self.assertRaises(ExpectedError, gulp, [None, None], keyfunc)

        data = [None, None, None, 20]
        assert list(map(lambda x: x[0], list(groupby(data)))) == [None, 20]

    def test_generators(self):
        # test that generators are accepted like input values
        
        def g(seqn):
            for i in seqn:
                yield i

        def g2(seqn):
            for i in seqn:
                yield (i, i)
        
        self.assertEqual(list(accumulate(g([1,2]))), [1, 3])
        self.assertEqual(list(chain(g([1,2]), g([3,4]))), [1, 2, 3, 4])
        self.assertEqual(list(combinations(g([1,2]), 2)), [(1, 2)])
        self.assertEqual(list(combinations_with_replacement(g([1,2]), 2)), [(1, 1), (1, 2), (2, 2)])
        self.assertEqual(list(dropwhile(lambda a:0, g([1,2]))), [1, 2])
        self.assertEqual(list(filterfalse(lambda a:0, g([1,2]))), [1, 2])
        self.assertEqual(list(groupby(g([1,2])))[0][0], 1)
        self.assertEqual(list(islice(g([1,2,3]),2)), [1, 2])
        self.assertEqual(list(permutations(g([1,2]), 2)), [(1, 2), (2, 1)])
        self.assertEqual(list(product(g([1,2]), repeat=2)), [(1, 1), (1, 2), (2, 1), (2, 2)])
        self.assertEqual(list(starmap(pow, g2([2,3]))), [4, 27])
        c = cycle(g([1,2]))
        self.assertEqual(next(c), 1)
        self.assertEqual(list(takewhile(lambda a:a, g([1,2]))), [1, 2])
        self.assertEqual(list(tee(g([1, 2]))[0]), [1, 2])
        self.assertEqual(list(zip_longest(g2([2,3]))), [((2, 2),), ((3, 3),)])

    @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 10), "skipping for cPython versions < 3.10")
    def test_pairwise_drained(self):
        p = pairwise("abcd")
        self.assertEqual(list(p), [('a', 'b'), ('b', 'c'), ('c', 'd')])
        self.assertEqual(list(p), [])
