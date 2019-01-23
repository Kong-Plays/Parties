package com.alessiodp.parties.common.commands.sub;

import com.alessiodp.parties.api.events.common.party.IPartyRenameEvent;
import com.alessiodp.parties.common.PartiesPlugin;
import com.alessiodp.parties.common.commands.utils.AbstractCommand;
import com.alessiodp.parties.common.commands.utils.CommandData;
import com.alessiodp.parties.common.configuration.Constants;
import com.alessiodp.parties.common.configuration.data.ConfigParties;
import com.alessiodp.parties.common.configuration.data.Messages;
import com.alessiodp.parties.common.logging.LogLevel;
import com.alessiodp.parties.common.logging.LoggerManager;
import com.alessiodp.parties.common.parties.objects.PartyImpl;
import com.alessiodp.parties.common.players.PartiesPermission;
import com.alessiodp.parties.common.players.objects.PartyPlayerImpl;
import com.alessiodp.parties.common.user.User;
import com.alessiodp.parties.common.utils.PartiesUtils;

public class CommandRename extends AbstractCommand {
	
	public CommandRename(PartiesPlugin instance) {
		super(instance);
	}
	
	@Override
	public boolean preRequisites(CommandData commandData) {
		User sender = commandData.getSender();
		PartyPlayerImpl pp = plugin.getPlayerManager().getPlayer(sender.getUUID());
		
		/*
		 * Checks for command prerequisites
		 */
		if (!sender.hasPermission(PartiesPermission.RENAME.toString())) {
			pp.sendNoPermission(PartiesPermission.RENAME);
			return false;
		}
		
		commandData.setPartyPlayer(pp);
		commandData.addPermission(PartiesPermission.ADMIN_RENAME_OTHERS);
		return true;
	}
	
	@Override
	public void onCommand(CommandData commandData) {
		PartyPlayerImpl pp = commandData.getPartyPlayer();
		
		/*
		 * Command handling
		 */
		PartyImpl party = null;
		Type type = Type.WRONGCMD;
		if (commandData.getArgs().length == 2) {
			// Own party
			if (!pp.getPartyName().isEmpty())
				party = plugin.getPartyManager().getParty(pp.getPartyName());
			type = Type.OWN;
		} else if (commandData.getArgs().length == 3) {
			// Another party
			if (commandData.havePermission(PartiesPermission.ADMIN_RENAME_OTHERS)) {
				party = plugin.getPartyManager().getParty(commandData.getArgs()[1]);
				type = Type.ANOTHER;
			}
		}
		
		if (party == null) {
			switch (type) {
			case OWN:
				// No party
				pp.sendMessage(Messages.PARTIES_COMMON_NOTINPARTY);
				break;
			case ANOTHER:
				// Party doesn't exist
				pp.sendMessage(Messages.PARTIES_COMMON_PARTYNOTFOUND
						.replace("%party%", commandData.getArgs()[1]));
				break;
			case WRONGCMD:
				// Wrong command
				if (commandData.havePermission(PartiesPermission.ADMIN_RENAME_OTHERS))
					pp.sendMessage(Messages.MAINCMD_RENAME_WRONGCMD_ADMIN);
				else
					pp.sendMessage(Messages.MAINCMD_RENAME_WRONGCMD);
			}
			return;
		}
		
		if (type.equals(Type.OWN) && !plugin.getRankManager().checkPlayerRankAlerter(pp, PartiesPermission.PRIVATE_ADMIN_RENAME))
			return;
		
		String partyName = commandData.getArgs()[(type.equals(Type.OWN) ? 1 : 2)]; // type == 1 ? args[1] : args[2]
		
		if (partyName.length() > ConfigParties.GENERAL_NAME_MAXLENGTH) {
			pp.sendMessage(Messages.MAINCMD_CREATE_NAMETOOLONG);
			return;
		}
		if (partyName.length() < ConfigParties.GENERAL_NAME_MINLENGTH) {
			pp.sendMessage(Messages.MAINCMD_CREATE_NAMETOOSHORT);
			return;
		}
		if (!PartiesUtils.checkAllowedCharacters(ConfigParties.GENERAL_NAME_ALLOWEDCHARS, partyName, Constants.DEBUG_CMD_CREATE_REGEXERROR_AC)) {
			pp.sendMessage(Messages.MAINCMD_CREATE_INVALIDNAME);
			return;
		}
		if (PartiesUtils.checkCensor(ConfigParties.GENERAL_NAME_CENSORREGEX, partyName, Constants.DEBUG_CMD_CREATE_REGEXERROR_CEN)) {
			pp.sendMessage(Messages.MAINCMD_CREATE_CENSORED);
			return;
		}
		if (plugin.getPartyManager().existParty(partyName)) {
			pp.sendMessage(Messages.MAINCMD_CREATE_NAMEEXISTS
					.replace("%party%", partyName));
			return;
		}
		
		/*
		 * Command starts
		 */
		String oldPartyName = party.getName();
		
		// Calling API event
		IPartyRenameEvent partiesRenameEvent = plugin.getEventManager().preparePartyRenameEvent(party, partyName, pp, type.equals(Type.ANOTHER));
		plugin.getEventManager().callEvent(partiesRenameEvent);
		
		partyName = partiesRenameEvent.getNewPartyName();
		if (!partiesRenameEvent.isCancelled()) {
			party.renameParty(partyName);
			
			pp.sendMessage(Messages.MAINCMD_RENAME_RENAMED
					.replace("%old%", oldPartyName), party);
			party.sendBroadcast(pp, Messages.MAINCMD_RENAME_BROADCAST
					.replace("%old%", oldPartyName));
			
			LoggerManager.log(LogLevel.BASIC, Constants.DEBUG_CMD_RENAME
					.replace("{player}", pp.getName())
					.replace("{value}", oldPartyName)
					.replace("{party}", party.getName()), true);
		} else {
			LoggerManager.log(LogLevel.DEBUG, Constants.DEBUG_API_RENAMEEVENT_DENY
					.replace("{party}", partyName)
					.replace("{player}", pp.getName()), true);
		}
	}
	
	private enum Type {
		OWN, ANOTHER, WRONGCMD
	}
}