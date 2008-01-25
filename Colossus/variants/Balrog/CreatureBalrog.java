package Balrog;


import java.util.Set;

import net.sf.colossus.variant.CreatureType;
import net.sf.colossus.variant.HazardTerrain;


/**
 * Custom class implementing the Balrog Creature. 
 * 
 * It is a DemiLord yet isn't immortal, and it's Image Name is Balrog no matter what is it's Creature Name.
 * 
 * @version $Id$
 * @author Romain Dolbeau
 */
public class CreatureBalrog extends CreatureType
{
    private int localMaxCount;

    public CreatureBalrog(String name, Integer power, Integer skill,
        Boolean rangestrikes, Boolean flies,
        Set<HazardTerrain> nativeTerrrains, Boolean nativeSlope,
        Boolean nativeRiver, Boolean waterDwelling, Boolean magicMissile,
        Boolean summonable, Boolean lord, Boolean demilord, Integer maxCount,
        String pluralName, String baseColor)
    {
        super(name, power.intValue(), skill.intValue(), rangestrikes
            .booleanValue(), flies.booleanValue(), nativeTerrrains,
            nativeSlope.booleanValue(), nativeRiver.booleanValue(),
            waterDwelling.booleanValue(), magicMissile.booleanValue(),
            summonable.booleanValue(), lord.booleanValue(), demilord
                .booleanValue(), maxCount.intValue(), pluralName, baseColor);
        localMaxCount = maxCount.intValue();
    }

    @Override
    public boolean isImmortal()
    { // demilord yet not immortal
        return false;
    }

    @Override
    public String getImageName()
    {
        return "Balrog";
    }

    @Override
    public String getDisplayName()
    {
        return "Balrog";
    }

    @Override
    public int getMaxCount()
    {
        return localMaxCount;
    }

    void setNewMaxCount(int count)
    {
        this.localMaxCount = count;
    }
}
