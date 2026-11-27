package app.qi.qingtiao;

final class NodeFeatures {
    String text = "", desc = "", viewId = "", className = "", activityClass = "";
    boolean ruleIdHit, ruleVerified, isDedicatedActivity, windowHasAdMarker, hasScrollableAncestor;
    int[] boundsInScreen = new int[4];
    int screenW, screenH;
    long windowAgeMs;
}
