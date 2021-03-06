package org.usermanagement.controller;

import java.util.List;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.usermanagement.model.Message;
import org.usermanagement.model.Role;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.RoleService;
import org.usermanagement.service.UserService;

/**
 * The Class UserController.
 */
@RestController
@RequestMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;
    
    @Autowired
    private ServerContext serverContext;
    
    /**
     * Gets the users by role id.
     *
     * @param roleId the role id
     * @return the users by role id
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/role/{role_id}", method = RequestMethod.GET)
    @Permissions(values = {IConstants.Permission.UM_ROLE_VIEW_USERS})
    public Role getUsersByRoleId(@PathVariable("role_id") final Long roleId) {
        LOGGER.info("[getUsersByRoleId] (roleId: " + roleId + ")");
        return roleService.getUserByRoleId(roleId);
    }

    /**
     * Creates user request.
     *
     * @param userInfo the request
     * @return the user info
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.POST)
    @Permissions(values = {IConstants.Permission.UM_USER_ADD})
	public UserInfo createUser(@RequestBody final UserInfo userInfo) {
		LOGGER.info("[createUser] (username: " + userInfo.getUsername() + ", name: " + userInfo.getName() + ")");
		return userService.createUser(userInfo);
	}

    /**
     * Update user.
     *
     * @param userInfo the request
     * @param userId the user id
     * @return the user info
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_USER_EDIT})
	public UserInfo updateUser(@RequestBody final UserInfo userInfo, @PathVariable("user_id") final Long userId) {
		LOGGER.info("[updateUser] (id: " + userId + ")");
		userInfo.setUserId(userId);
		return userService.updateUser(userInfo, userId);
	}

    /**
     * Gets the user list.
     *
     * @return the user list
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(method = RequestMethod.GET)
    public List<UserInfo> getUsers() {
        LOGGER.info("[getUsers]");
        return userService.getAllUsers();
    }

    /**
     * Gets the user by id.
     *
     * @param userId the user id
     * @return the user by id
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.GET)
    public UserInfo getUserById(@PathVariable("user_id") final Long userId) {
        LOGGER.info("[getUserById] (id: " + userId + ")");
        UserInfo userInfo = userService.getUserById(userId);
        return userInfo;
    }


    /**
     * Delete user by id.
     *
     * @param userId the user id
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequestMapping(value = "/{user_id}", method = RequestMethod.DELETE)
    @Permissions(values = {IConstants.Permission.UM_USER_DELETE})
	public void deleteUserById(@PathVariable("user_id") final Long userId) {
		LOGGER.info("[deleteUserById] (id: " + userId + ")");
		userService.deleteUserById(userId);
	}

    /**
     * Assign users by role id.
     *
     * @param roleId the role id
     * @param role the request
     * @return the role
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/role/{role_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_ASSIGN_ROLE_TO_USERS})
	public Role assignUsersByRoleId(@PathVariable("role_id") final Long roleId, @RequestBody final Role role) {
		LOGGER.info("[assignUsersByRoleId] (roleId: " + roleId + ")");
		return userService.assignUserByRoleId(roleId, role);
	}

    /**
     * Change password.
     *
     * @param userInfo the request
     * @param userId the user id
     * @param httpRequest the http request
     * @return the reset password
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/changePassword/{user_id}", method = RequestMethod.PUT)
	public Message changePassword(@RequestBody final UserInfo userInfo, @PathVariable("user_id") final Long userId) {
		LOGGER.info("[changePassword] (userId: " + userId + ")");
		userService.changePassword(userInfo, userId);
		return new Message("Password has been changed successfully.");
	}

    /**
     * Reset password.
     *
     * @param userId the user id
     * @return the object
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/resetpassword/{id}", method = RequestMethod.GET)
    @Permissions(values = {IConstants.Permission.UM_USER_RESET})
	public Object resetPassword(@PathVariable("id") final Long userId) {
		LOGGER.info("[resetPassword] (userId: " + userId + ")");
		userService.resetPassword(userId, false);
		return new Message("Password has been sent to your EmailId");
	}

    /**
     * Reset password by admin.
     *
     * @param userId the user id
     * @return the object
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/admin/resetpassword/{id}", method = RequestMethod.GET)
    @Permissions(values = {IConstants.Permission.UM_USER_RESET_ADMIN})
	public Object resetPasswordByAdmin(@PathVariable("id") final Long userId) {
		LOGGER.info("[resetPasswordByAdmin] (userId: " + userId + ")");
		return userService.resetPassword(userId, true);
	}

    /**
     * Reset twofa.
     *
     * @param userId the user id
     * @return the message
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/reset2fa/{user_id}", method = RequestMethod.PUT)
    @Permissions(values = {IConstants.Permission.UM_USER_RESET2FA})
	public Message resetTwofa(@PathVariable("user_id") final Long userId) {
		LOGGER.info("[resetTwofa] (userId: " + userId + ")");
		userService.reset2fa(userId);
		return new Message("2FA has been reset for the user.");
	}
    
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = "/settings", method = RequestMethod.GET)
	public String getUserSettings() {
		LOGGER.info("[getUserSettings] (userId: " + serverContext.getRequestContext().getUserId() + ")");
		return userService.getUserSettings(serverContext.getRequestContext().getUserId());
	}

	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = "/settings", method = RequestMethod.PATCH)
	public String saveOrUpdateSettings(@RequestBody final String data) {
		UserInfo userInfo = new UserInfo();
		userInfo.setData(data);
		userInfo.setUserId(serverContext.getRequestContext().getUserId());
		LOGGER.info("[saveOrUpdateSettings] (userId: " + userInfo.getUserId() + ")");
		userService.saveOrUpdateSettings(userInfo);
		return data;
	}
}
