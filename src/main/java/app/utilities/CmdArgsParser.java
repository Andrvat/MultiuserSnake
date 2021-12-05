package app.utilities;

import me.alexpanov.net.FreePortFinder;
import org.apache.commons.cli.*;

import java.util.*;

public class CmdArgsParser {
    private static final String DEFAULT_PLAYER_NAME = "First player";
    private static final String DEFAULT_HOST_ADDRESS = "0.0.0.0";

    private final Options cmdOptions = new Options();

    private String playerName;
    private String hostInetAddress;
    private int hostPort;

    public CmdArgsParser() {
        OptionSettings playerNameSettings = OptionSettings.builder()
                .opt("n")
                .longOpt("playerName")
                .hasArg(true)
                .description("Name of host player that will be visible to " +
                        "all other players in the current session")
                .build();
        OptionSettings hostInetAddressSettings = OptionSettings.builder()
                .opt("a")
                .longOpt("inetAddress")
                .hasArg(true)
                .description("Network address of host where other players can connect to his game")
                .build();
        OptionSettings hostPortSettings = OptionSettings.builder()
                .opt("p")
                .longOpt("hostPort")
                .hasArg(true)
                .description("Host's network port, through which other players can connect to his game")
                .build();
        addAllSettingsToOptions(Arrays.asList(playerNameSettings, hostInetAddressSettings, hostPortSettings));
    }

    private void addAllSettingsToOptions(List<OptionSettings> optionSettings) {
        for (OptionSettings option : optionSettings) {
            cmdOptions.addOption(option.getOpt(),
                    option.getLongOpt(),
                    option.getHasArg(),
                    option.getDescription());
        }
    }

    public void parseArguments(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(cmdOptions, args);

        playerName = commandLine.getOptionValue("n");
        if (null == playerName) {
            playerName = DEFAULT_PLAYER_NAME;
        }

        hostInetAddress = commandLine.getOptionValue("a");
        if (null == hostInetAddress) {
            hostInetAddress = DEFAULT_HOST_ADDRESS;
        }

        try {
            hostPort = Integer.parseInt(commandLine.getOptionValue("p"));
        } catch (Exception e) {
            hostPort = FreePortFinder.findFreeLocalPort();
        }
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getHostInetAddress() {
        return hostInetAddress;
    }

    public int getHostPort() {
        return hostPort;
    }

    public Options getCmdOptions() {
        return cmdOptions;
    }

    @Override
    public String toString() {
        return "CmdArgsParser{" +
                "cmdOptions=" + cmdOptions +
                ", playerName='" + playerName + '\'' +
                ", hostInetAddress='" + hostInetAddress + '\'' +
                ", hostPort=" + hostPort +
                '}';
    }
}
