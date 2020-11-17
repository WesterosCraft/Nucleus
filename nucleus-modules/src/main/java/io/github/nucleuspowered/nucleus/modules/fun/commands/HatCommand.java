/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.fun.commands;

import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.modules.fun.FunPermissions;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandExecutor;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandResult;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.Command;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.EssentialsEquivalent;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.item.inventory.ItemStack;

@EssentialsEquivalent({"hat", "head"})
@Command(
        aliases = {"hat", "head"},
        basePermission = FunPermissions.BASE_HAT,
        commandDescriptionKey = "hat",
        associatedPermissions = FunPermissions.OTHERS_HAT
)
public class HatCommand implements ICommandExecutor {

    @Override
    public Parameter[] parameters(final INucleusServiceCollection serviceCollection) {
        return new Parameter[] {
                serviceCollection.commandElementSupplier().createOnlyOtherUserPermissionElement(FunPermissions.OTHERS_HAT)
        };
    }

    @Override
    public ICommandResult execute(final ICommandContext context) throws CommandException {
        final ServerPlayer pl = context.getPlayerFromArgs();
        final boolean isSelf = context.is(pl);
        final ItemStack helment = pl.getHead();

        final ItemStack stack = pl.getItemInHand(HandTypes.MAIN_HAND);
        if (stack.isEmpty()) {
            return context.errorResult("command.generalerror.handempty");
        }
        final ItemStack hand = stack.copy();
        hand.setQuantity(1);
        pl.setHead(hand);
        final Component itemName = hand.getType().asComponent();

        final GameMode gameMode = pl.get(Keys.GAME_MODE).orElseGet(GameModes.NOT_SET);
        if (gameMode != GameModes.CREATIVE) {
            if (stack.getQuantity() > 1) {
                stack.setQuantity(stack.getQuantity() - 1);
                pl.setItemInHand(HandTypes.MAIN_HAND, stack);
            } else {
                pl.setItemInHand(HandTypes.MAIN_HAND, null);
            }
        }

        // If the old item can't be placed back in the subject inventory, drop the item.
        if (!helment.isEmpty()) {
            Util.getStandardInventory(pl).offer(helment)
                    .getRejectedItems().forEach(x -> Util.dropItemOnFloorAtLocation(x, pl.getServerLocation().getWorld(),
                        pl.getLocation().getPosition()));
        }

        if (!isSelf) {
            context.sendMessage(
                    "command.hat.success",
                    context.getServiceCollection().playerDisplayNameService().getDisplayName(pl.getUniqueId()),
                    itemName);
        }

        context.sendMessageTo(pl, "command.hat.successself", itemName);
        return context.successResult();
    }
}