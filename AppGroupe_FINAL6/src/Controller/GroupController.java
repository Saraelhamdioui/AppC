package Controller;


import DAO.GroupDao;
import DAO.GroupMessageDao;
import model.Group;
import model.GroupMember;
import model.GroupMessage;
import network.Protocol;

import java.util.List;

public class GroupController {
    private GroupDao groupDao;
    private GroupMessageDao messageDao;

    public GroupController() {
        this.groupDao = new GroupDao();
        this.messageDao = new GroupMessageDao();
    }

    public int createGroup(String groupName, String creator) {
        Group group = new Group(groupName, creator);
        return groupDao.createGroup(group);
    }

    public void addMember(int groupId, String username, String role) {
        groupDao.addMember(groupId, username, role);
    }

    public void removeMember(int groupId, String username) {
        groupDao.removeMember(groupId, username);
    }

    public List<Group> getUserGroups(String username) {
        return groupDao.getUserGroups(username);
    }

    public List<GroupMember> getGroupMembers(int groupId) {
        return groupDao.getGroupMembers(groupId);
    }

    public boolean isMember(int groupId, String username) {
        return groupDao.isMember(groupId, username);
    }

    public void deleteGroup(int groupId) {
        groupDao.deleteGroup(groupId);
    }

    public int sendGroupMessage(int groupId, String sender, String content) {
        GroupMessage message = new GroupMessage(groupId, sender, content);
        return messageDao.saveMessage(message);
    }

    public List<GroupMessage> getGroupHistory(int groupId, int limit) {
        return messageDao.getGroupMessages(groupId, limit);
    }

    public void markGroupMessagesAsSeen(int groupId, String username) {
        messageDao.markAsSeen(groupId, username);
    }

    // Méthodes pour la communication réseau
    public String processGroupCommand(String command, String username) {
        String[] parts = command.split(":", 5);

        if (parts[0].equals(Protocol.GROUP_CREATE)) {
            // GROUP_CREATE:nom_groupe
            String groupName = parts[1];
            int groupId = createGroup(groupName, username);
            return Protocol.GROUP_CREATE + ":" + groupId + ":" + groupName;

        } else if (parts[0].equals(Protocol.GROUP_MSG)) {
            // GROUP_MSG:groupId:sender:content
            int groupId = Integer.parseInt(parts[1]);
            String sender = parts[2];
            String content = parts[3];
            int msgId = sendGroupMessage(groupId, sender, content);
            return Protocol.GROUP_MSG + ":" + groupId + ":" + msgId + ":" + sender + ":" + content;

        } else if (parts[0].equals(Protocol.GROUP_ADD_MEMBER)) {
            // GROUP_ADD_MEMBER:groupId:newMember
            int groupId = Integer.parseInt(parts[1]);
            String newMember = parts[2];
            addMember(groupId, newMember, "MEMBER");
            return Protocol.GROUP_ADD_MEMBER + ":" + groupId + ":" + newMember;

        } else if (parts[0].equals(Protocol.GROUP_REMOVE_MEMBER)) {
            // GROUP_REMOVE_MEMBER:groupId:memberToRemove
            int groupId = Integer.parseInt(parts[1]);
            String memberToRemove = parts[2];
            removeMember(groupId, memberToRemove);
            return Protocol.GROUP_REMOVE_MEMBER + ":" + groupId + ":" + memberToRemove;

        } else if (parts[0].equals(Protocol.GROUP_LIST)) {
            List<Group> groups = getUserGroups(username);
            StringBuilder sb = new StringBuilder(Protocol.GROUP_LIST + ":");
            for (Group g : groups) {
                sb.append(g.getId()).append("|").append(g.getName()).append(",");
            }
            return sb.toString();

        } else if (parts[0].equals(Protocol.GROUP_MEMBERS)) {
            int groupId = Integer.parseInt(parts[1]);
            List<GroupMember> members = getGroupMembers(groupId);
            StringBuilder sb = new StringBuilder(Protocol.GROUP_MEMBERS + ":" + groupId + ":");
            for (GroupMember m : members) {
                sb.append(m.getUsername()).append(",");
            }
            return sb.toString();
        }

        return "ERROR:Unknown command";
    }
}