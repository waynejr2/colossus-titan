package net.sf.colossus.server;

import java.util.Random;
import net.sf.colossus.util.DevRandom;

/**
 * Class Dice handles die-rolling
 * @version $Id$
 * @author David Ripton
 * @author Romain Dolbeau
 */
public final class Dice
{
    private static Random random = new DevRandom();
    
    /** Put all die rolling in one place, in case we decide to change random
     *  number algorithms, use an external dice server, etc. */
    public static int rollDie()
    {
        return rollDie(6);
    }
    
    public static int rollDie(int size)
    {
        return random.nextInt(size) + 1;
    }
}
