package com.github.kargone.skyblockflips.flip;

import com.github.kargone.skyblockflips.util.ModConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dashboard's "Active Auctions" and "Current Investments" tables, priced and
 * ready to draw: ports of {@code updateAuctions} and {@code processInvestments}.
 *
 * Like {@link CraftFlipRanking}, the rows are rebuilt on the background thread
 * after every data refresh and handed to the renderer as immutable lists.
 *
 * Investments worth more than {@link ModConfig#bigInvestmentWorth()} at their
 * current sell price are big long term holds, listed in the Market Reminders
 * window. The Current Investments panel lists only what is actively being
 * flipped: short term investments under that worth that would make at least
 * {@link ModConfig#minInvestmentProfit()} over all their lots.
 */
public final class PositionValues {

    /**
     * One auction row.
     *
     * @param lowestBin    what the item is worth now, the dashboard's "Current lBin"
     * @param profit       what it makes if it sells at its listed price
     * @param relistProfit what it makes relisted at the lowest BIN, after paying the listing fee again
     */
    public record AuctionRow(String itemName, double lowestBin, double priceSelling, double materialCost,
                             double profit, double relistProfit, boolean sold) {
    }

    /**
     * One investment row.
     *
     * @param highestPrice    the most any lot of it cost
     * @param margin          after-tax sell price less the highest price
     * @param profitAtHighest the margin over the whole amount, as if every lot cost the highest price
     * @param profit          the sum over the lots, each at what it actually cost
     * @param boughtAt        when the earliest lot was bought, or 0 when unknown
     * @param lots            every lot, most expensive first, as the dashboard's sub-orders table has them
     */
    public record InvestmentRow(String name, boolean longTerm, double amount, double highestPrice, double sellPrice,
                                double margin, double profitAtHighest, double profit, long boughtAt,
                                List<LotRow> lots) {

        /** What the whole amount is worth at the current sell price. */
        public double worth() {
            return amount * sellPrice;
        }

        /** Tells this investment apart from one of the same name in the other bucket. */
        public String key() {
            return (longTerm ? "long:" : "short:") + name;
        }
    }

    /**
     * One lot of an investment.
     *
     * @param price  what it was bought or crafted for
     * @param margin after-tax sell price less that price
     * @param profit the margin over the lot
     */
    public record LotRow(double amount, double price, double margin, double profit, long timeBought) {
    }

    private static volatile List<AuctionRow> auctions = List.of();
    private static volatile List<InvestmentRow> investments = List.of();
    private static volatile List<InvestmentRow> bigInvestments = List.of();
    private static volatile boolean loaded = false;

    private PositionValues() {
    }

    /** Auctions in the order the server keeps them, like the dashboard. */
    public static List<AuctionRow> auctions() {
        return auctions;
    }

    /** Short term investments under the big investment worth and over the minimum profit, best all-lots profit first. */
    public static List<InvestmentRow> investments() {
        return investments;
    }

    /** Investments over the big investment worth, best all-lots profit first. */
    public static List<InvestmentRow> bigInvestments() {
        return bigInvestments;
    }

    /** The investment with this {@link InvestmentRow#key()} in either list, or null when neither has it. */
    public static InvestmentRow findInvestment(String key) {
        for (InvestmentRow row : investments) {
            if (row.key().equals(key)) return row;
        }
        for (InvestmentRow row : bigInvestments) {
            if (row.key().equals(key)) return row;
        }
        return null;
    }

    /** Whether this investment is one of the big ones, shown in the Market Reminders window. */
    public static boolean isBig(String key) {
        for (InvestmentRow row : bigInvestments) {
            if (row.key().equals(key)) return true;
        }
        return false;
    }

    /** False until trader data has been priced at least once. */
    public static boolean loaded() {
        return loaded;
    }

    public static void clear() {
        auctions = List.of();
        investments = List.of();
        bigInvestments = List.of();
        loaded = false;
    }

    public static void recompute(ProductList products, MarketData market, TraderPositions positions) {
        if (!positions.loaded()) {
            clear();
            return;
        }

        CraftCostModel model = new CraftCostModel(products, market);

        List<AuctionRow> auctionRows = new ArrayList<>(positions.auctions().size());
        for (TraderPositions.Auction auction : positions.auctions()) {
            double lowestBin = model.marketValueOf(auction.itemName());
            double selling = auction.priceSelling();
            double feeRate = SkyblockFees.auctionFeeRate(selling);
            double profit = selling * (1 - feeRate) - auction.materialCost();
            // As the dashboard has it: the fee rate of the current listing, and its
            // fee paid a second time to relist.
            double relistProfit = lowestBin * (1 - feeRate) - auction.materialCost() - selling * feeRate;
            auctionRows.add(new AuctionRow(auction.itemName(), lowestBin, selling, auction.materialCost(),
                    profit, relistProfit, auction.sold()));
        }

        double afterTax = 1 - SkyblockFees.bazaarTaxRate();
        double bigWorth = ModConfig.bigInvestmentWorth();
        double minProfit = ModConfig.minInvestmentProfit();
        List<InvestmentRow> small = new ArrayList<>();
        List<InvestmentRow> big = new ArrayList<>();
        for (TraderPositions.Investment investment : positions.investments()) {
            double sellPrice = model.marketValueOf(investment.name());

            double amount = 0D;
            double highestPrice = 0D;
            double profit = 0D;
            long boughtAt = 0L;
            List<LotRow> lots = new ArrayList<>(investment.lots().size());
            for (TraderPositions.Lot lot : investment.lots()) {
                double lotMargin = sellPrice * afterTax - lot.price();
                amount += lot.amount();
                highestPrice = Math.max(highestPrice, lot.price());
                profit += lotMargin * lot.amount();
                if (lot.timeBought() > 0 && (boughtAt == 0L || lot.timeBought() < boughtAt)) boughtAt = lot.timeBought();
                lots.add(new LotRow(lot.amount(), lot.price(), lotMargin, lotMargin * lot.amount(), lot.timeBought()));
            }
            double margin = sellPrice * afterTax - highestPrice;
            lots.sort(Comparator.comparingDouble(LotRow::price).reversed());

            InvestmentRow row = new InvestmentRow(investment.name(), investment.longTerm(), amount, highestPrice,
                    sellPrice, margin, margin * amount, profit, boughtAt, List.copyOf(lots));
            if (row.worth() > bigWorth) {
                big.add(row);
            } else if (!row.longTerm() && row.profit() >= minProfit) {
                small.add(row);
            }
        }

        // On the profit over the lots at what each really cost, not the dashboard's
        // highest-price profit.
        Comparator<InvestmentRow> bestFirst = Comparator.comparingDouble(InvestmentRow::profit).reversed();
        small.sort(bestFirst);
        big.sort(bestFirst);

        auctions = List.copyOf(auctionRows);
        investments = List.copyOf(small);
        bigInvestments = List.copyOf(big);
        loaded = true;
    }
}
