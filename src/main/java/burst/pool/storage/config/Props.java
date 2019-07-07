package burst.pool.storage.config;

import burst.kit.entity.BurstAddress;

import java.util.Objects;

public class Props {
    public static final Prop<Integer> serverPort = new Prop<>("serverPort", 80); // Must be > 0, < 2^16
    public static final Prop<String> nodeAddresses = new Prop<>("nodeAddresses", ""); // Must be non-empty
    public static final Prop<String> poolName = new Prop<>("poolName", "");

    public static final Prop<String> passphrase = new Prop<>("passphrase", ""); // Must be non-empty

    public static final Prop<String> dbUrl = new Prop<>("dbUrl", "");
    public static final Prop<String> dbUsername = new Prop<>("dbUsername", "");
    public static final Prop<String> dbPassword = new Prop<>("dbPassword", "");

    public static final Prop<Integer> nAvg = new Prop<>("nAvg", 360); // Must be ?
    public static final Prop<Integer> nMin = new Prop<>("nMin", 1); // Must be ?
    public static final Prop<Integer> tMin = new Prop<>("tMin", 20); // Must be ?
    public static final Prop<Long> maxDeadline = new Prop<>("maxDeadline", Long.MAX_VALUE); // Must be > 0
    public static final Prop<Integer> processLag = new Prop<>("processLag", 10); // Must be > 0

    public static final Prop<BurstAddress> feeRecipient = new Prop<>("feeRecipient", null); // Must be non null
    public static final Prop<Float> poolFeePercentage = new Prop<>("poolFeePercentage", 0f); // Must be 0-1
    public static final Prop<Float> winnerRewardPercentage = new Prop<>("winnerRewardPercentage", 0f); // Must be 0-1

    public static final Prop<Float> defaultMinimumPayout = new Prop<>("defaultMinimumPayout", 100f); // Must be > 0
    public static final Prop<Float> minimumMinimumPayout = new Prop<>("minimumMinimumPayout", 100f); // Must be > 0
    public static final Prop<Integer> minPayoutsPerTransaction = new Prop<>("minPayoutsPerTransaction", 10); // Must be 2-64
    public static final Prop<Float> transactionFee = new Prop<>("transactionFee", 1f); // Must be > 0.00735
    public static final Prop<Integer> payoutRetryCount = new Prop<>("payoutRetryCount", 3);
    public static final Prop<Integer> submitNonceRetryCount = new Prop<>("submitNonceRetryCount", 3);

    public static final Prop<String> siteTitle = new Prop<>("site.title", "Burst Pool");
    public static final Prop<String> siteIconIco = new Prop<>("site.icon.ico", "icon.ico");
    public static final Prop<String> siteIconPng = new Prop<>("site.icon.png", "icon.png");
    public static final Prop<String> siteNodeAddress = new Prop<>("site.nodeAddress", "https://wallet.burst-alliance.org:8125/");
    public static final Prop<String> softwarePackagesAddress = new Prop<>("site.softwarePackagesAddress", "https://github.com/burst-apps-team");
    public static final Prop<String> siteDiscordLink = new Prop<>("site.discord", "https://discord.gg/ms6eagX");

    public static void validateProperties(PropertyService propertyService) {
        int serverPort = propertyService.getInt(Props.serverPort);
        if (serverPort <= 0 || serverPort >= Math.pow(2, 16)) {
            throw new IllegalArgumentException("Illegal server port: " + serverPort + " (Must be 0-2^16 exclusive)");
        }

        String nodeAddress = propertyService.getString(Props.nodeAddresses);
        if (nodeAddress == null || Objects.equals(nodeAddress, "")) {
            throw new IllegalArgumentException("Illegal node address (empty)");
        }

        String poolName = propertyService.getString(Props.poolName);
        if (poolName == null || Objects.equals(poolName, "")) {
            throw new IllegalArgumentException("Illegal pool name (empty)");
        }

        String passphrase = propertyService.getString(Props.passphrase);
        if (passphrase == null || Objects.equals(passphrase, "")) {
            throw new IllegalArgumentException("Illegal passphrase (empty)");
        }

        int nAvg = propertyService.getInt(Props.nAvg);
        // Todo

        int nMin = propertyService.getInt(Props.nMin);
        // Todo

        int tMin = propertyService.getInt(Props.tMin);
        // Todo

        long maxDeadline = propertyService.getLong(Props.maxDeadline);
        if (maxDeadline <= 0) {
            throw new IllegalArgumentException("Illegal maxDeadline: " + maxDeadline + " (Must be > 0)");
        }

        int processLag = propertyService.getInt(Props.processLag);
        if (processLag < 0) {
            throw new IllegalArgumentException("Illegal processLag: " + processLag + " (Must be > 0)");
        }

        BurstAddress feeRecipient = propertyService.getBurstAddress(Props.feeRecipient);
        if (feeRecipient == null) {
            throw new IllegalArgumentException("Illegal feeRecipient (not set)");
        }

        float poolFeePercentage = propertyService.getFloat(Props.poolFeePercentage);
        if (poolFeePercentage < 0f || poolFeePercentage > 1f) {
            throw new IllegalArgumentException("Illegal poolFeePercentage: " + poolFeePercentage + " (Must be 0-1)");
        }

        float winnerRewardPercentage = propertyService.getFloat(Props.winnerRewardPercentage);
        if (winnerRewardPercentage < 0f || winnerRewardPercentage > 1f) {
            throw new IllegalArgumentException("Illegal winnerRewardPercentage: " + winnerRewardPercentage + " (Must be 0-1)");
        }

        float minimumMinimumPayout = propertyService.getFloat(Props.minimumMinimumPayout);
        if (minimumMinimumPayout <= 0) {
            throw new IllegalArgumentException("Illegal minimumMinimumPayout: " + processLag + " (Must be > 0)");
        }

        float defaultMinimumPayout = propertyService.getFloat(Props.defaultMinimumPayout);
        if (defaultMinimumPayout < minimumMinimumPayout) {
            throw new IllegalArgumentException("Illegal defaultMinimumPayout: " + processLag + " (Must be > minimumMinimumPayout)");
        }

        int minPayoutsPerTransaction = propertyService.getInt(Props.minPayoutsPerTransaction);
        if (minPayoutsPerTransaction < 2 || minPayoutsPerTransaction > 64) {
            throw new IllegalArgumentException("Illegal minPayoutsPerTransaction: " + minPayoutsPerTransaction + " (Must be 2-64)");
        }

        float transactionFee = propertyService.getFloat(Props.transactionFee);
        if (transactionFee < 0.00735f) {
            throw new IllegalArgumentException("Illegal minPayoutsPerTransaction: " + minPayoutsPerTransaction + " (Must be > 0.00735)");
        }
    }
}
