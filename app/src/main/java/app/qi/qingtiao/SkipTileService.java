package app.qi.qingtiao;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 通知栏快捷磁贴：零额外权限，一键暂停或恢复轻跳。 */
public final class SkipTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Scope scope = new Scope(this);
        scope.enable(!scope.enabled());
        syncTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = new Scope(this).enabled();
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("轻跳");
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(enabled ? "运行中" : "已暂停");
        }
        tile.updateTile();
    }
}
