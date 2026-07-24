/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * A container class summarizing an enemy nation.
 */
public class NationSummary extends FreeColObject {

    public static final String TAG = "nationSummary";

    /** The stance of the player toward the requesting player. */
    private Stance stance;

    /** The number of settlements this player has. */
    private int numberOfSettlements;

    /** The number of units this (European) player has. */
    private int numberOfUnits;

    /** The military strength of this (European) player. */
    private int militaryStrength;

    /** The naval strength of this (European) player. */
    private int navalStrength;

    /** The gold this (European) player has. */
    private int gold;

    /** The (European) player SoL. */
    private int soL;

    /** The number of founding fathers this (European) player has. */
    private int foundingFathers;

    /** The tax rate of this (European) player. */
    private int tax;

    /**
     * The (European) player's merchant-marine strength: total cargo capacity
     * of its naval units, regardless of combat role.  Unlike {@link #soL},
     * {@link #foundingFathers} and {@link #tax} this is never gated behind
     * {@code Ability.BETTER_FOREIGN_AFFAIRS_REPORT} — the classic-UI's Foreign
     * Affairs report needs it unconditionally (see
     * {@code net.sf.freecol.client.gui.classic.ClassicReportForeignAffairPanel}).
     */
    private int mercantileMarine;

    /**
     * The (European) player's rebel/loyalist head counts, derived from its
     * population ({@link #numberOfUnits}) and {@link Player#getSoL()}.  Also
     * unconditional, for the same reason as {@link #mercantileMarine}.
     */
    private int rebels;
    private int loyalists;


    /**
     * Trivial constructor allowing creation by Game.newInstance().
     */
    public NationSummary() {
        setId(""); // Identifiers unnecessary
    }

    /**
     * Creates a nation summary for the specified player.
     *
     * @param player The {@code Player} to create the summary for.
     * @param requester The {@code Player} making the request.
     */
    public NationSummary(Player player, Player requester) {
        this();

        stance = player.getStance(requester);
        if (stance == Stance.UNCONTACTED) stance = Stance.PEACE;

        numberOfSettlements = player.getSettlementCount();

        if (player.isEuropean()) {
            numberOfUnits = player.getUnitCount();
            militaryStrength = player.calculateStrength(false);
            navalStrength = player.calculateStrength(true);
            gold = player.getGold();
            mercantileMarine = computeMercantileMarine(player);
            final int sol = player.getSoL();
            rebels = (numberOfUnits * sol) / 100;
            loyalists = numberOfUnits - rebels;
            if (player == requester || requester
                .hasAbility(Ability.BETTER_FOREIGN_AFFAIRS_REPORT)) {
                soL = player.getSoL();
                foundingFathers = player.getFatherCount();
                tax = player.getTax();
            } else {
                soL = foundingFathers = tax = -1;
            }
        } else {
            numberOfUnits = militaryStrength = navalStrength = gold = soL
                = foundingFathers = tax = mercantileMarine = rebels
                = loyalists = -1;
        }
    }

    /**
     * Total cargo capacity of {@code player}'s naval units, regardless of
     * combat role — the merchant-marine strength shown in the classic UI's
     * Foreign Affairs report.
     *
     * @param player The {@code Player} to sum the fleet capacity of.
     * @return The total cargo capacity.
     */
    public static int computeMercantileMarine(Player player) {
        int total = 0;
        for (Unit u : player.getUnitSet()) {
            if (u.isNaval()) total += u.getCargoCapacity();
        }
        return total;
    }


    // Trivial accessors
    public Stance getStance() {
        return stance;
    }

    public int getNumberOfSettlements() {
        return numberOfSettlements;
    }

    public int getNumberOfUnits() {
        return numberOfUnits;
    }

    public int getMilitaryStrength() {
        return militaryStrength;
    }

    public int getNavalStrength() {
        return navalStrength;
    }

    public int getGold() {
        return gold;
    }

    public int getFoundingFathers() {
        return foundingFathers;
    }

    public int getSoL() {
        return soL;
    }

    public int getTax() {
        return tax;
    }

    public int getMercantileMarine() {
        return mercantileMarine;
    }

    public int getRebels() {
        return rebels;
    }

    public int getLoyalists() {
        return loyalists;
    }


    // Overide FreeColObject

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends FreeColObject> boolean copyIn(T other) {
        NationSummary o = copyInCast(other, NationSummary.class);
        if (o == null || !super.copyIn(o)) return false;
        this.stance = o.getStance();
        this.numberOfSettlements = o.getNumberOfSettlements();
        this.numberOfUnits = o.getNumberOfUnits();
        this.militaryStrength = o.getMilitaryStrength();
        this.navalStrength = o.getNavalStrength();
        this.gold = o.getGold();
        this.soL = o.getSoL();
        this.foundingFathers = o.getFoundingFathers();
        this.tax = o.getTax();
        this.mercantileMarine = o.getMercantileMarine();
        this.rebels = o.getRebels();
        this.loyalists = o.getLoyalists();
        return true;
    }


    // Serialization

    private static final String FOUNDING_FATHERS_TAG = "foundingFathers";
    private static final String GOLD_TAG = "gold";
    private static final String MILITARY_STRENGTH_TAG = "militaryStrength";
    private static final String NAVAL_STRENGTH_TAG = "navalStrength";
    private static final String NUMBER_OF_SETTLEMENTS_TAG = "numberOfSettlements";
    private static final String NUMBER_OF_UNITS_TAG = "numberOfUnits";
    private static final String SOL_TAG = "SoL";
    private static final String STANCE_TAG = "stance";
    private static final String TAX_TAG = "tax";
    private static final String MERCANTILE_MARINE_TAG = "mercantileMarine";
    private static final String REBELS_TAG = "rebels";
    private static final String LOYALISTS_TAG = "loyalists";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(NUMBER_OF_SETTLEMENTS_TAG, numberOfSettlements);

        xw.writeAttribute(NUMBER_OF_UNITS_TAG, numberOfUnits);

        xw.writeAttribute(MILITARY_STRENGTH_TAG, militaryStrength);

        xw.writeAttribute(NAVAL_STRENGTH_TAG, navalStrength);

        xw.writeAttribute(STANCE_TAG, stance);

        xw.writeAttribute(GOLD_TAG, gold);

        xw.writeAttribute(MERCANTILE_MARINE_TAG, mercantileMarine);

        xw.writeAttribute(REBELS_TAG, rebels);

        xw.writeAttribute(LOYALISTS_TAG, loyalists);

        if (soL >= 0) {
            xw.writeAttribute(SOL_TAG, soL);
        }

        if (foundingFathers >= 0) {
            xw.writeAttribute(FOUNDING_FATHERS_TAG, foundingFathers);
        }

        if (tax >= 0) {
            xw.writeAttribute(TAX_TAG, tax);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        stance = xr.getAttribute(STANCE_TAG, Stance.class, Stance.PEACE);

        numberOfSettlements = xr.getAttribute(NUMBER_OF_SETTLEMENTS_TAG, -1);

        numberOfUnits = xr.getAttribute(NUMBER_OF_UNITS_TAG, -1);

        militaryStrength = xr.getAttribute(MILITARY_STRENGTH_TAG, -1);

        navalStrength = xr.getAttribute(NAVAL_STRENGTH_TAG, -1);

        gold = xr.getAttribute(GOLD_TAG, -1);

        mercantileMarine = xr.getAttribute(MERCANTILE_MARINE_TAG, -1);

        rebels = xr.getAttribute(REBELS_TAG, -1);

        loyalists = xr.getAttribute(LOYALISTS_TAG, -1);

        soL = xr.getAttribute(SOL_TAG, -1);

        foundingFathers = xr.getAttribute(FOUNDING_FATHERS_TAG, -1);

        tax = xr.getAttribute(TAX_TAG, -1);
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return TAG; }
}
