package com.gillodaby.betterportals;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.concurrent.CompletableFuture;

/**
 * /bportal target <name>
 * /bportal link <name>
 * /bportal cancel
 * /bportal remove <name>
 * /bportal list
 * /bportal reload
 */
final class BetterPortalsCommand extends AbstractCommand {

    private final BetterPortalsService service;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> targetNameArg;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> linkNameArg;
    private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> removeNameArg;

    BetterPortalsCommand(BetterPortalsService service) {
        super("bportal", "Configure BetterPortals links");
        this.service = service;
        addAliases("bp");

        AbstractCommand target = new AbstractCommand("target", "Set a portal target at your position") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleTarget(ctx);
            }
        };
        this.targetNameArg = target.withRequiredArg("name", "portal name", ArgTypes.STRING);
        target.requirePermission("betterportals.admin");
        addSubCommand(target);

        AbstractCommand link = new AbstractCommand("link", "Bind a teleporter block to a portal name") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleLink(ctx);
            }
        };
        this.linkNameArg = link.withRequiredArg("name", "portal name", ArgTypes.STRING);
        link.requirePermission("betterportals.admin");
        addSubCommand(link);

        AbstractCommand cancel = new AbstractCommand("cancel", "Cancel the pending link") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleCancel(ctx);
            }
        };
        cancel.requirePermission("betterportals.admin");
        addSubCommand(cancel);

        AbstractCommand remove = new AbstractCommand("remove", "Remove a portal name and its links") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleRemove(ctx);
            }
        };
        this.removeNameArg = remove.withRequiredArg("name", "portal name", ArgTypes.STRING);
        remove.requirePermission("betterportals.admin");
        addSubCommand(remove);

        AbstractCommand list = new AbstractCommand("list", "List portal targets") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleList(ctx);
            }
        };
        addSubCommand(list);

        AbstractCommand reload = new AbstractCommand("reload", "Reload config.yaml") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleReload(ctx);
            }
        };
        reload.requirePermission("betterportals.admin");
        addSubCommand(reload);

        AbstractCommand help = new AbstractCommand("help", "Show help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                return handleHelp(ctx);
            }
        };
        addSubCommand(help);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        return handleHelp(ctx);
    }

    private CompletableFuture<Void> handleTarget(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can set portal targets."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        String name = ctx.get(targetNameArg);
        if (name == null || name.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /bportal target <name>"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.setTarget(player, name);
        if (!ok) {
            ctx.sendMessage(service.text("Failed to save target (missing world/position)."));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Target set for portal '" + name + "'."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleLink(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can link portals."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        String name = ctx.get(linkNameArg);
        if (name == null || name.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /bportal link <name>"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.beginLink(player, name);
        if (!ok) {
            ctx.sendMessage(service.text("Unknown portal name. Use /bportal target <name> first."));
            return CompletableFuture.completedFuture(null);
        }
        ctx.sendMessage(service.text("Right-click a teleporter to bind it to '" + name + "'."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleCancel(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(service.text("Only players can cancel links."));
            return CompletableFuture.completedFuture(null);
        }
        Player player = ctx.senderAs(Player.class);
        boolean ok = service.cancelLink(player);
        ctx.sendMessage(service.text(ok ? "Pending link cancelled." : "No pending link."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleRemove(CommandContext ctx) {
        String name = ctx.get(removeNameArg);
        if (name == null || name.isEmpty()) {
            ctx.sendMessage(service.text("Usage: /bportal remove <name>"));
            return CompletableFuture.completedFuture(null);
        }
        boolean ok = service.removeLink(name);
        ctx.sendMessage(service.text(ok ? "Removed portal '" + name + "'." : "Portal name not found."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleList(CommandContext ctx) {
        service.syncLinksFromWarps();
        StringBuilder sb = new StringBuilder("Portals:");
        int count = 0;
        for (PortalLink link : service.listLinks()) {
            count++;
            sb.append("\n").append(count).append(". ").append(link.name())
                    .append(" -> ").append(link.world())
                    .append(" [").append(trim(link.x())).append(", ")
                    .append(trim(link.y())).append(", ")
                    .append(trim(link.z())).append("]");
        }
        if (count == 0) {
            sb.append("\n(none)");
        }
        ctx.sendMessage(service.text(sb.toString()));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleReload(CommandContext ctx) {
        service.reload();
        ctx.sendMessage(service.text("Reloaded BetterPortals config."));
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> handleHelp(CommandContext ctx) {
        String help = "BetterPortals commands:" +
            "\n/bportal help - show this help" +
            "\n/bportal target <name> - save your current position as a portal destination" +
            "\n/bportal link <name> - right-click a teleporter to bind it to a destination" +
            "\n/bportal cancel - cancel the pending link" +
            "\n/bportal remove <name> - delete a portal destination and its links" +
            "\n/bportal list - list all portal destinations" +
            "\n/bportal reload - reload BetterPortals config" +
            "\nTip: use /bp as a shortcut for /bportal";
        ctx.sendMessage(service.text(help));
        return CompletableFuture.completedFuture(null);
    }

    private String trim(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }
}
