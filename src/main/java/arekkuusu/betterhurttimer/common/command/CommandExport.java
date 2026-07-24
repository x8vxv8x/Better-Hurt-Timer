package arekkuusu.betterhurttimer.common.command;

import arekkuusu.betterhurttimer.BHT;
import arekkuusu.betterhurttimer.common.RuntimeData;
import com.google.common.collect.ImmutableList;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CommandExport extends CommandBase {

    private static final String[] SUBCOMMANDS = {"damageFrames", "attackFrames", "mobIdListAll"};

    @Override
    public String getName() {
        return BHT.MOD_ID + "_export";
    }

    @Override
    public List<String> getAliases() {
        return ImmutableList.of("bht_export");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "Usage: /" + getName() + " <damageFrames|attackFrames|mobIdListAll>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        return super.getTabCompletions(server, sender, args, targetPos);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new CommandException("command.bht_export.no_subcommand");
        }

        String subCommand = args[0];
        if (!isValidSubCommand(subCommand)) {
            throw new CommandException("command.bht_export.invalid_subcommand", subCommand);
        }

        File exportDir = new File(server.getDataDirectory(), "config/bht");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new CommandException("command.bht_export.dir_failed");
        }

        File exportFile = new File(exportDir, subCommand + ".txt");
        boolean existed = exportFile.exists();

        try (FileWriter writer = new FileWriter(exportFile)) {
            switch (subCommand) {
                case "damageFrames":
                    for (Map.Entry<String, Integer> entry : RuntimeData.DAMAGE_SOURCE_TIMES.entrySet()) {
                        writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
                    }
                    break;
                case "attackFrames":
                    for (Map.Entry<ResourceLocation, Double> entry : RuntimeData.ATTACK_THRESHOLDS.entrySet()) {
                        writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
                    }
                    break;
                case "mobIdListAll":
                    for (ResourceLocation location : GameRegistry.findRegistry(EntityEntry.class).getKeys()) {
                        writer.write(location + "\n");
                    }
                    break;
            }
        } catch (IOException e) {
            BHT.LOG.error("Export failed", e);
            throw new CommandException("command.bht_export.unsuccessful");
        }

        sendMessage(sender, existed ? "export.overwritten" : "export.created");
        sendMessage(sender, "export.successful");
    }

    private boolean isValidSubCommand(String subCommand) {
        for (String valid : SUBCOMMANDS) {
            if (valid.equals(subCommand)) {
                return true;
            }
        }
        return false;
    }

    private void sendMessage(ICommandSender sender, String type, Object... args) {
        String key = "command." + BHT.MOD_ID + "." + type;
        sender.sendMessage(new TextComponentTranslation(key, args));
    }
}
