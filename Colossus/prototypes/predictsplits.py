#!/usr/bin/env python2

__version__ = "$Id$"

import sys
import copy

"""Prototype of Colossus split prediction"""

killValue = { 'Titan':1242, 'Angel':248, 'Archangel':366, 'Behemoth':240,
        'Centaur':122, 'Colossus':404, 'Cyclops':181, 'Dragon':299,
        'Gargoyle':124, 'Giant':285, 'Gorgon':189, 'Griffon':208,
        'Guardian':245, 'Hydra':309, 'Lion':150, 'Minotaur':165,
        'Ogre':121, 'Ranger':169, 'Serpent':361, 'Troll':161,
        'Unicorn':244, 'Warbear':180, 'Warlock':213, 'Wyvern':214 }


def creatureComp(creature1, creature2):
    """Sort creatures in decreasing order of importance.  Keep identical
       creatures together with a secondary sort by creature name."""
    diff = killValue[creature2.name] - killValue[creature1.name]
    if diff != 0:
        return diff
    return cmp(creature1.name, creature2.name)

def superset(big, little):
    """Return True if list big is a superset of list little."""
    for el in little:
        if big.count(el) < little.count(el):
            return False
    return True

def subtractLists(big, little):
    assert superset(big, little)
    li = big[:]
    for el in little:
        li.remove(el)
    return li

def numCreature(li, creatureName):
    count = 0
    for ci in li:
        if ci.name == creatureName:
            count += 1
    return count


def getCreatureInfo(li, creatureName):
    """Return the first CreatureInfo that matches the passed name."""
    for ci in li:
        if ci.name == creatureName:
            return ci
    return None

def getCreatureNames(li):
    return [ci.name for ci in li]

def removeLastUncertainCreature(li):
    li.reverse()
    for ii, ci in enumerate(li):
        if not ci.certain:
            del li[ii]    # li.remove(ci) can remove the wrong one
            li.reverse()
            return
    li.reverse()
    raise RuntimeError, "No uncertain creatures"


def minCount(lili, name):
    """ lili is a list of lists.  Return the minimum number of times
        name appears in any of the lists contained in lili. """
    min_ = sys.maxint
    for li in lili:
        min_ = min(li.count(name), min_)
    # If we never saw it, reduce to zero
    if min_ == sys.maxint:
        min_ = 0
    return min_


class Perms:
    """ Based on Gagan Saksena's code in online Python cookbook
        Not reentrant or thread-safe. """
    def __init__(self):
        self.retlist = []

    def findCombinations(self, alist, numb):
        self.retlist = []
        self._findCombinations(alist, numb, [])
        return self.retlist

    def _findCombinations(self, alist, numb, blist):
        if not numb:
            self.retlist.append(blist[:])
            return
        for i in range(len(alist)):
            blist.append(alist[i])
            self._findCombinations(alist[i+1:], numb-1, blist)
            blist.pop()

    def findPermutations(self, alist, numb):
        self.retlist = []
        self._findPermutations(alist, numb, [])
        return self.retlist

    def _findPermutations(self, alist, numb, blist):
        if not numb:
            self.retlist.append(blist[:])
            return
        for i in range(len(alist)):
            blist.append(alist.pop(i))
            self._findPermutations(alist, numb-1, blist)
            alist.insert(i, blist.pop())


class CreatureInfo:
    def __init__(self, name, certain, atSplit):
        self.name = name
        self.certain = certain
        self.atSplit = atSplit

    def __eq__(self, other):
        """Two CreatureInfo objects match if the names match."""
        return self.name == other.name

    def __str__(self):
        s = self.name
        if not self.certain:
            s += '?'
        if not self.atSplit:
            s += '*'
        return s

    def __repr__(self):
        return self.__str__()

    def __hash__(self):
        """Two CreatureInfo objects match if the names match."""
        return self.name


class Node:
    def __init__(self, markerId, turnCreated, creatures, parent):
        self.markerId = markerId        # Not unique!
        self.turnCreated = turnCreated
        self.creatures = creatures      # list of CreatureInfo
        self.removed = []               # list of CreatureInfo, only if atSplit
        self.parent = parent
        self._clearChildren()

    def _clearChildren(self):
        # All are at the time this node was split.
        self.childSize1 = 0   # childSize1 >= childSize2
        self.childSize2 = 0
        self.child1 = None    # child1 is the presumed "better" legion
        self.child2 = None
        self.turnSplit = -1

    def fullname(self):
        return self.markerId + '(' + str(self.turnCreated) + ")"

    def __str__(self):
        """Show a string with both current and removed creatures."""
        self.creatures.sort(creatureComp)
        s = self.fullname() + ":"
        for ci in self.creatures:
            s += " " + str(ci)
        for ci in self.removed:
            s += " " + str(ci) + "-"
        return s

    def __repr__(self):
        return str(self)

    def getCertainCreatures(self):
        """Return list of CreatureInfo where certain is true."""
        return [ci for ci in self.creatures if ci.certain]

    def allCertain(self):
        """Return True if all creatures are certain."""
        for ci in self.creatures:
            if not ci.certain:
                return False
        return True

    def allDescendentsCertain(self):
        """Return true if all of this node's children, grandchildren, etc.
           have no uncertain creatures."""
        if self.child1 is None:
            assert self.child2 is None
            return True
        else:
            return (self.child1.allCertain() and self.child2.allCertain()
              and self.child2.allDescendentsCertain() 
              and self.child2.allDescendentsCertain())

    def getAtSplitOrRemovedCreatures(self):
        """Return list of CreatureInfo where atSplit is true, plus removed
           creatures."""
        alive = [ci for ci in self.creatures if ci.atSplit]
        dead = self.removed[:]
        return alive + dead

    def getAfterSplitCreatures(self):
        """Return list of CreatureInfo where atSplit is false."""
        return [ci for ci in self.creatures if not ci.atSplit]

    def getCertainAtSplitOrRemovedCreatures(self):
        """Return list of CreatureInfo where both certain and atSplit are
           true, plus removed creatures."""
        alive = [ci for ci in self.creatures if ci.certain and ci.atSplit]
        dead = self.removed[:]
        return alive + dead

    def getOtherChild(self, child):
        if child == self.child1:
            return self.child2
        elif child == self.child2:
            return self.child1
        raise RuntimeError, "Not my child in Node.getOtherChild"

    def getOtherChildMarkerId(self):
        if self.child1.markerId != self.markerId:
            return self.child1.markerId
        else:
            return self.child2.markerId

    def getHeight(self):
        return len(self.creatures)

    def revealCreatures(self, cnl):
        """cnl is a list of creature names"""
        if ((not cnl) or 
          (superset(getCreatureNames(self.getCertainCreatures()), cnl)
           and (self.allDescendentsCertain()))):
            return

        cil = [CreatureInfo(name, True, True) for name in cnl]

        # Use a copy rather than the original so we can remove
        # creatures as we check for multiples.
        dupe = copy.deepcopy(cil)

        # Confirm that all creatures that were certain still fit
        # along with the revealed creatures.
        count = len(dupe)
        for ci in self.getCertainCreatures():
            if ci in dupe:
                dupe.remove(ci)
            else:
                count += 1

        if count > self.getHeight():
            err = ("Certainty error in Node.revealCreatures() count=%d "
                  "height=%d") % (count, self.getHeight())
            raise RuntimeError, err

        # Then mark passed creatures as certain and then
        # communicate this to the parent, to adjust other legions.

        dupe = copy.deepcopy(cil)
        count = 0
        for ci in dupe:
            ci.certain = True
            ci.atSplit = True   # If not atSplit, would be certain.
            if numCreature(self.creatures, ci.name) < numCreature(dupe,
                    ci.name):
                self.creatures.append(ci)
                count += 1

        # Ensure that the creatures in cnl are now marked as certain
        dupe = copy.deepcopy(cil)
        for ci in self.getCertainCreatures():
            if ci in dupe:
                dupe.remove(ci)
        for ci in dupe:
            for ci2 in self.creatures:
                if not ci2.certain and ci2.name == ci.name:
                    ci2.certain = True
                    break

        # Need to remove count uncertain creatures.
        for unused in range(count):
            removeLastUncertainCreature(self.creatures)
        if self.parent is not None:
            self.parent._updateChildContents()


    def _childCreaturesMatch(self):
        """Return True if creatures in children are consistent with self."""
        if self.child1 is None:
            assert self.child2 is None
            return True
        all = (self.child1.getAtSplitOrRemovedCreatures() +
                self.child2.getAtSplitOrRemovedCreatures())
        for ci in all:
            if numCreature(all, ci.name) != numCreature(self.creatures, 
              ci.name):
                return False
        return True

    def _isLegalInitialSplitoff(self):
        if self.getHeight() != 4:
            return False
        names = getCreatureNames(self.creatures)
        return (names.count("Titan") + names.count("Angel") == 1)

    def _findAllPossibleSplits(self, childSize, knownKeep, knownSplit):
        """ Return a list of all legal combinations of splitoffs. """
        # Sanity checks
        if len(knownSplit) > childSize:
            raise RuntimeError, "More known splitoffs than splitoffs"
        creatures = self.creatures
        if len(creatures) > 8:
            raise RuntimeError, "More than 8 creatures in legion at split"
        elif len(creatures) == 8:
            if childSize != 4:
                raise RuntimeError, "Illegal initial split"
            if not "Titan" in getCreatureNames(self.creatures):
                raise RuntimeError, "No titan in 8-high legion"
            if not "Angel" in getCreatureNames(self.creatures):
                raise RuntimeError, "No angel in 8-high legion"

        knownCombo = knownSplit + knownKeep
        if not superset(creatures, knownCombo):
            self.revealCreatures(getCreatureNames(knownCombo))
            return self._findAllPossibleSplits(childSize, knownKeep, knownSplit)

        unknowns = copy.deepcopy(creatures)
        for ci in knownCombo:
            unknowns.remove(ci)

        numUnknownsToSplit = childSize - len(knownSplit)

        perms = Perms()
        unknownCombos = perms.findCombinations(unknowns, numUnknownsToSplit)

        possibleSplits = []
        for combo in unknownCombos:
            pos = copy.deepcopy(knownSplit) + copy.deepcopy(combo)
            if self.getHeight() != 8:
                possibleSplits.append(pos)
            else:
                posnode = Node(None, None, pos, self)
                if posnode._isLegalInitialSplitoff():
                    possibleSplits.append(pos)
        return possibleSplits


    def _chooseCreaturesToSplitOut(self, pos):
        """Decide how to split this legion, and return a list of Creatures to
           remove.  Return empty list on error."""
        maximize = (2 * len(pos[0]) > self.getHeight())

        bestKillValue = None
        creaturesToRemove = []
        for li in pos:
            totalKillValue = 0
            for creature in li:
                totalKillValue += killValue[creature.name]
            if ((bestKillValue is None) or
                    (maximize and totalKillValue > bestKillValue) or
                    (not maximize and totalKillValue < bestKillValue)):
                bestKillValue = totalKillValue
                creaturesToRemove = li
        return creaturesToRemove


    def split(self, childSize, otherMarkerId, turn=-1):
        if len(self.creatures) > 8:
            raise RuntimeError, "More than 8 creatures in legion"

        if turn == -1:
            turn = self.turnSplit   # Re-predicting earlier split
        else:
            self.turnSplit = turn   # New split

        knownKeep = []
        knownSplit = []
        if self.child1 is not None:
            assert self.child2 is not None
            knownKeep += self.child1.getCertainAtSplitOrRemovedCreatures()
            knownSplit += self.child2.getCertainAtSplitOrRemovedCreatures()

        pos = self._findAllPossibleSplits(childSize, knownKeep, knownSplit)
        splitoffCreatures = self._chooseCreaturesToSplitOut(pos)

        splitoffNames = getCreatureNames(splitoffCreatures)

        if self.allCertain():
            creatureNames = getCreatureNames(self.creatures)
            posSplitNames = []
            posKeepNames = []
            for li in pos:
                names = getCreatureNames(li)
                posSplitNames.append(names)
                posKeepNames.append(subtractLists(creatureNames, names))
            knownKeepNames = []
            knownSplitNames = []
            for name in creatureNames:
                if not name in knownKeepNames:
                    minKeep = minCount(posKeepNames, name)
                    for unused in range(minKeep):
                        knownKeepNames.append(name)
                if not name in knownSplitNames:
                    minSplit = minCount(posSplitNames, name)
                    for unused in range(minSplit):
                        knownSplitNames.append(name)

        # If either knownKeep or knownSplit is the full size of that
        # child legion, then the certainty of creatures in the other
        # child legion is the same as in the parent.
        else:
            if len(knownSplit) == childSize:
                certain = self.getCertainCreatures()
                for ci in knownSplit:
                    certain.remove(ci)
                if not superset(certain, knownKeep):
                    raise RuntimeError, "certain not superset of knownKeep"
                knownKeep = certain

            elif len(knownKeep) == len(self.creatures) - childSize:
                certain = self.getCertainCreatures()
                for ci in knownKeep:
                    certain.remove(ci)
                if not superset(certain, knownSplit):
                    raise RuntimeError, "certain not superset of knownSplit"
                knownSplit = certain

            knownKeepNames = getCreatureNames(knownKeep)
            knownSplitNames = getCreatureNames(knownSplit)

        # lists of CreatureInfo
        strongList = []
        weakList = []
        for ci in self.creatures:
            name = ci.name
            newinfo = CreatureInfo(name, False, True)
            if name in splitoffNames:
                weakList.append(newinfo)
                splitoffNames.remove(name)
                # If in knownSplit, set certain
                if name in knownSplitNames:
                    knownSplitNames.remove(name)
                    newinfo.certain = True
            else:
                strongList.append(newinfo)
                # If in knownKeep, set certain
                if name in knownKeepNames:
                    knownKeepNames.remove(name)
                    newinfo.certain = True

        if self.child1 is None:
            assert self.child2 is None
            afterSplit1 = []
            afterSplit2 = []
            removed1 = []
            removed2 = []
        else:
            afterSplit1 = self.child1.getAfterSplitCreatures()
            afterSplit2 = self.child2.getAfterSplitCreatures()
            removed1 = self.child1.removed
            removed2 = self.child2.removed

        marker1 = self.markerId
        marker2 = otherMarkerId

        strongFinal = strongList + afterSplit1
        for ci in removed1:
            strongFinal.remove(ci)
        weakFinal = weakList + afterSplit2
        for ci in removed2:
            weakFinal.remove(ci)

        if self.child1 is None: 
            assert self.child2 is None
            self.child1 = Node(marker1, turn, strongFinal, self)
            self.child2 = Node(marker2, turn, weakFinal, self)
        else:
            self.child1.creatures = strongFinal
            self.child2.creatures = weakFinal

        if self.childSize1 == 0:
            assert self.childSize2 == 0
            self.childSize1 = self.child1.getHeight()
            self.childSize2 = self.child2.getHeight()

        self.child1._resplitDescendents()
        self.child2._resplitDescendents()


    def merge(self, other, turn):
        """Recombine this legion and other, because it was not possible to 
           move either one. The two legions must share the same parent.  If 
           either legion has the parent's markerId, then that legion will 
           remain. Otherwise this legion will remain."""
        parent = self.parent
        assert parent == other.parent
        if (parent.markerId == self.markerId or
                parent.markerId == other.markerId):
            # Remove self and other from parent, as if the split never
            # happened.  The parent will then be a leaf node.
            parent._clearChildren()
        else:
            # Remove self and other, then resplit parent into self.
            parent._clearChildren()
            parent.split(self.getHeight() + other.getHeight(),
              self.markerId, turn)


    def _updateChildContents(self):
        """Tell this parent legion the updated contents of its children."""
        names = []
        for child in [self.child1, self.child2]:
            names.extend(getCreatureNames(
              child.getCertainAtSplitOrRemovedCreatures()))
        self.revealCreatures(names)
        self.split(self.childSize2, self.getOtherChildMarkerId())

    def _resplitDescendents(self):
        """Recursively resplit all this legion's descendents."""
        if self.child1 is not None:
            self.split(self.childSize2, self.getOtherChildMarkerId())

    def addCreature(self, creatureName):
        if (self.getHeight() >= 7 and self.child1 is None):
            raise RuntimeError, "Tried adding to 7-high legion"
        ci = CreatureInfo(creatureName, True, False)
        self.creatures.append(ci)


    def removeCreature(self, creatureName):
        if (self.getHeight() <= 0):
            raise RuntimeError, "Tried removing from 0-high legion"
        self.revealCreatures([creatureName])
        ci = getCreatureInfo(self.creatures, creatureName)

        # Only need to track the removed creature for future parent split
        # predictions if it was here at the time of the split.
        if ci.atSplit:
            self.removed.append(ci)
        self.creatures.remove(ci)

    def removeCreatures(self, creatureNames):
        self.revealCreatures(creatureNames)
        for name in creatureNames:
            self.removeCreature(name)


class PredictSplits:
    def __init__(self, playerName, rootId, creatureNames):
        self.playerName = playerName
        # All creatures in root legion must be known

        infoList = []
        for name in creatureNames:
            info = CreatureInfo(name, True, True)
            infoList.append(info)
        self.root = Node(rootId, 0, infoList, None)

    def getNodes(self, node):
        """Return all nodes in tree."""
        nodes = [node]
        if node.child1 is not None:
            nodes += self.getNodes(node.child1) + self.getNodes(node.child2)
        return nodes

    def getLeaves(self, node):
        """Return all non-empty childless nodes in tree."""
        leaves = []
        if node.child1 is None:
            if len(node.creatures) > 0:
                leaves.append(node)
        else:
            leaves += self.getLeaves(node.child1) + self.getLeaves(node.child2)

        # If duplicate markerIds, prune the older node.
        for leaf1 in leaves:
            for leaf2 in leaves:
                if leaf1 != leaf2 and leaf1.markerId == leaf2.markerId:
                    if leaf1.turnCreated == leaf2.turnCreated:
                        raise (RuntimeError,
                                "Two leaf nodes with same markerId and turn")
                    elif leaf1.turnCreated < leaf2.turnCreated:
                        leaves.remove(leaf1)
                    else:
                        leaves.remove(leaf2)

        return leaves

    def printLeaves(self):
        """Print all childless nodes in tree, in string order. """
        print
        leaves = self.getLeaves(self.root)
        leaves.sort(lambda a,b: cmp(str(a), str(b)))
        for leaf in leaves:
            print leaf
        print

    def printNodes(self):
        """Print all nodes in tree, in string order. """
        print
        nodes = self.getNodes(self.root)
        nodes.sort(lambda a,b: cmp((a.turnCreated, a.markerId), 
          (b.turnCreated, b.markerId)))
        for node in nodes:
            print node
        print

    def dumpAsDot(self, filename=None):
        """Dump all nodes in the tree in Graphviz dot format"""
        if filename is None:
            f = sys.stdout
        else:
            f = open(filename, "w")
        li = []
        li.append("digraph G {")
        for node in self.getNodes(self.root):
            if node.child1:
                assert node.child2
                li.append('"%s" -> "%s";' % (str(node), str(node.child1)))
                li.append('"%s" -> "%s";' % (str(node), str(node.child2)))
        li.append("}")
        s = "\n".join(li)
        print >>f, s

    def getLeaf(self, markerId):
        """Return the leaf node with matching markerId"""
        leaves = self.getLeaves(self.root)
        for leaf in leaves:
            if leaf.markerId == markerId:
                return leaf
        return None
