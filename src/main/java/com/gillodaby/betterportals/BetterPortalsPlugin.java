package com.gillodaby.betterportals;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BetterPortalsPlugin extends JavaPlugin {

    private BetterPortalsService service;

    public BetterPortalsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        service = new BetterPortalsService(getDataDirectory());
        getEntityStoreRegistry().registerSystem(new BetterPortalsUseSystem(service));
        getEntityStoreRegistry().registerSystem(new BetterPortalsBreakSystem(service));
    }

    @Override
    protected void start() {
        CommandManager.get().register(new BetterPortalsCommand(service));
        service.syncLinksFromWarps();
        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> scheduled = (ScheduledFuture<Void>) HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
            service::syncLinksFromWarps,
            2,
            5,
            TimeUnit.SECONDS
        );
        getTaskRegistry().registerTask(scheduled);
        System.out.println("[BetterPortals] Started.");
    }

    @Override
    protected void shutdown() {
        if (service != null) {
            service.save();
        }
    }
}
