/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.mgt.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.core.model.IdentityEventListenerConfig;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.mgt.IdentityMgtConfig;
import org.wso2.carbon.identity.mgt.IdentityMgtEventListener;
import org.wso2.carbon.identity.mgt.IdentityMgtServiceException;
import org.wso2.carbon.identity.mgt.beans.UserIdentityMgtBean;
import org.wso2.carbon.identity.mgt.beans.VerificationBean;
import org.wso2.carbon.identity.mgt.dto.UserIdentityClaimDTO;
import org.wso2.carbon.identity.mgt.dto.UserIdentityClaimsDO;
import org.wso2.carbon.identity.mgt.dto.UserRecoveryDTO;
import org.wso2.carbon.identity.mgt.dto.UserRecoveryDataDO;
import org.wso2.carbon.identity.mgt.internal.IdentityMgtServiceComponent;
import org.wso2.carbon.identity.mgt.store.JDBCUserRecoveryDataStore;
import org.wso2.carbon.identity.mgt.store.UserIdentityDataStore;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.listener.UserOperationEventListener;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the Utility class used by the admin service to read and write
 * identity data.
 *
 * @author sga
 */
public class UserIdentityManagementUtil {
    private static final String EXISTING_USER = "Username already exists in the system";
    private static final String INVALID_CLAIM_URL = "InvalidClaimUrl";
    private static final String EXISTING_ROLE = "RoleExisting";
    private static final String READ_ONLY_STORE = "User store is read only";
    private static final String READ_ONLY_PRIMARY_STORE = "ReadOnlyPrimaryUserStoreManager";
    private static final String INVALID_ROLE = "InvalidRole";
    private static final String ANONYMOUS_USER = "AnonymousUser";
    private static final String INVALID_OPERATION = "InvalidOperation";
    private static final String NO_READ_WRITE_PERMISSIONS = "NoReadWritePermission";
    private static final String PASSWORD_INVALID = "Credential must be a non null string";
    private static final String SHARED_USER_ROLES = "SharedUserRoles";
    private static final String REMOVE_ADMIN_USER = "RemoveAdminUser";
    private static final String LOGGED_IN_USER = "LoggedInUser";
    private static final String ADMIN_USER = "AdminUser";
    private static final String NULL_CREDENTIALS = "NullCredetials";
    private static final String NULL_USERNAME = "User name must be a non null string";;

    private static VerificationBean vBean = new VerificationBean();
    private static Log log = LogFactory.getLog(UserIdentityManagementUtil.class);

    private UserIdentityManagementUtil() {
    }

    /**
     * Returns the registration information such as the temporary password or
     * the confirmation code
     *
     * @param userName
     * @param userStoreManager
     * @param tenantId
     * @return
     * @throws IdentityException
     */
    public static UserRecoveryDTO getUserIdentityRecoveryData(String userName,
                                                              UserStoreManager userStoreManager,
                                                              int tenantId)
            throws IdentityException {

        JDBCUserRecoveryDataStore metadatStore = new JDBCUserRecoveryDataStore();
        UserRecoveryDTO registrationDTO = new UserRecoveryDTO(userName);
        return registrationDTO;
    }

    /**
     * Locks the user account.
     *
     * @param userName
     * @param userStoreManager
     * @throws IdentityException
     */
    public static void lockUserAccount(String userName, UserStoreManager userStoreManager)
            throws IdentityException {
        if (!isIdentityMgtListenerEnable()) {
            throw IdentityException.error("Cannot lock account, IdentityMgtEventListener is not enabled.");
        }

        String domainName = ((org.wso2.carbon.user.core.UserStoreManager) userStoreManager).getRealmConfiguration().
                getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        userName = UserCoreUtil.addDomainToName(userName, domainName);

        try {
            if (!userStoreManager.isExistingUser(userName)) {
                log.error("User " + userName + " does not exist in tenant "+userStoreManager.getTenantId());
                throw IdentityException.error("No user account found for user " + userName);
            }
        } catch (UserStoreException e) {
            log.error("Error while reading user identity data", e);
            throw IdentityException.error("Error while lock user account : " + userName);

        }

        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO = store.load(UserCoreUtil.removeDomainFromName(userName), userStoreManager);
        if (userIdentityDO != null) {
            userIdentityDO.setAccountLock(true);
            userIdentityDO.setUnlockTime(0);
            store.store(userIdentityDO, userStoreManager);
        } else {
            throw IdentityException.error("No user account found for user " + userName);
        }
    }

    private static boolean isIdentityMgtListenerEnable() {
        IdentityEventListenerConfig identityEventListenerConfig = IdentityUtil.readEventListenerProperty
                (UserOperationEventListener.class.getName(), IdentityMgtEventListener.class.getName());
        if (identityEventListenerConfig == null) {
            return true;
        }

        if (StringUtils.isNotBlank(identityEventListenerConfig.getEnable())) {
            return Boolean.parseBoolean(identityEventListenerConfig.getEnable());
        } else {
            return true;
        }
    }

    /**
     * Unlocks the user account
     *
     * @param userName
     * @param userStoreManager
     * @throws IdentityException
     */
    public static void unlockUserAccount(String userName, UserStoreManager userStoreManager)
            throws IdentityException {

        if (!isIdentityMgtListenerEnable()) {
            throw IdentityException.error("Cannot unlock account, IdentityMgtEventListener is not enabled.");
        }

        String domainName = ((org.wso2.carbon.user.core.UserStoreManager) userStoreManager).getRealmConfiguration().
                getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        userName = UserCoreUtil.addDomainToName(userName, domainName);

        try {
            if (!userStoreManager.isExistingUser(userName)) {
                log.error("User " + userName + " does not exist in tenant "+userStoreManager.getTenantId());
                throw IdentityException.error("No user account found for user " + userName);
            }
        } catch (UserStoreException e) {
            log.error("Error while reading user identity data", e);
            throw IdentityException.error("Error while unlock user account " + userName);

        }

        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO = store.load(UserCoreUtil.removeDomainFromName(userName), userStoreManager);
        if (userIdentityDO != null) {
            userIdentityDO.setAccountLock(false);
            userIdentityDO.setUnlockTime(0);
            store.store(userIdentityDO, userStoreManager);
        } else {
            throw IdentityException.error("No user account found for user " + userName);
        }

    }

    /**
     * Returns an array of primary security questions
     *
     * @param tenantId
     * @return
     * @throws IdentityException
     */
    public static String[] getPrimaryQuestions(int tenantId) throws IdentityException {
        JDBCUserRecoveryDataStore store = new JDBCUserRecoveryDataStore();
        UserRecoveryDataDO[] metadata = store.load("TENANT", tenantId);
        if (metadata.length < 1) {
            return new String[0];
        }
        List<String> validSecurityQuestions = new ArrayList<String>();
        String[] questionsList = new String[validSecurityQuestions.size()];
        return validSecurityQuestions.toArray(questionsList);
    }

    /**
     * Add or update primary security questions
     *
     * @param primarySecurityQuestion
     * @param tenantId
     * @throws IdentityException
     */
    public static void addPrimaryQuestions(String[] primarySecurityQuestion, int tenantId) throws IdentityException {
        JDBCUserRecoveryDataStore store = new JDBCUserRecoveryDataStore();
        UserRecoveryDataDO[] metadata = new UserRecoveryDataDO[primarySecurityQuestion.length];
        int i = 0;
        for (String secQuestion : primarySecurityQuestion) {
            if (!secQuestion.contains(UserCoreConstants.ClaimTypeURIs.CHALLENGE_QUESTION_URI)) {
                throw IdentityException.error("One or more security questions does not contain the namespace " +
                        UserCoreConstants.ClaimTypeURIs.CHALLENGE_QUESTION_URI);
            }
            metadata[i++] =
                    new UserRecoveryDataDO("TENANT", tenantId,
                            UserRecoveryDataDO.METADATA_PRIMARAY_SECURITY_QUESTION,
                            secQuestion);
        }
        store.store(metadata);
    }

    /**
     * Remove primary security questions
     *
     * @param tenantId
     * @throws IdentityException
     */
    public static void removePrimaryQuestions(String[] primarySecurityQuestion, int tenantId) throws IdentityException {

        UserRecoveryDataDO[] metadata = new UserRecoveryDataDO[primarySecurityQuestion.length];
        int i = 0;
        for (String secQuestion : primarySecurityQuestion) {
            if (!secQuestion.contains(UserCoreConstants.ClaimTypeURIs.CHALLENGE_QUESTION_URI)) {
                throw IdentityException.error("One or more security questions does not contain the namespace " +
                        UserCoreConstants.ClaimTypeURIs.CHALLENGE_QUESTION_URI);
            }
            metadata[i++] =
                    new UserRecoveryDataDO("TENANT", tenantId,
                            UserRecoveryDataDO.METADATA_PRIMARAY_SECURITY_QUESTION,
                            secQuestion);
        }

    }

    // ---- Util methods for authenticated users ----///

    /**
     * Update security questions of the logged in user.
     *
     * @param securityQuestion
     * @param userStoreManager
     * @throws IdentityException
     */
    public static void updateUserSecurityQuestions(String userName, UserIdentityClaimDTO[] securityQuestion,
                                                   UserStoreManager userStoreManager)
            throws IdentityException {
        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO = store.load(userName, userStoreManager);
        if (userIdentityDO != null) {
            userIdentityDO.updateUserSequeiryQuestions(securityQuestion);
            store.store(userIdentityDO, userStoreManager);
        } else {
            throw IdentityException.error("No user account found for user " + userName);
        }
    }

    /**
     * Returns security questions of the logged in user
     *
     * @param userStoreManager
     * @return
     * @throws IdentityMgtServiceException
     */
    public static UserIdentityClaimDTO[] getUserSecurityQuestions(String userName,
                                                                  UserStoreManager userStoreManager)
            throws IdentityMgtServiceException {
        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO;
        userIdentityDO = store.load(userName, userStoreManager);
        if (userIdentityDO != null) {
            return userIdentityDO.getUserSequeiryQuestions();
        } else {
            throw new IdentityMgtServiceException("No user account found for user " + userName);
        }
    }

    /**
     * Updates users recovery data such as the phone number, email etc
     *
     * @param userStoreManager
     * @param userIdentityRecoveryData
     * @throws IdentityException
     */
    public static void updateUserIdentityClaims(String userName, UserStoreManager userStoreManager,
                                                UserIdentityClaimDTO[] userIdentityRecoveryData)
            throws IdentityException {

        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO = store.load(userName, userStoreManager);
        if (userIdentityDO != null) {
            userIdentityDO.updateUserIdentityRecoveryData(userIdentityRecoveryData);
            store.store(userIdentityDO, userStoreManager);
        } else {
            throw IdentityException.error("No user account found for user " + userName);
        }

    }

    /**
     * Returns all user claims which can be used in the identity recovery
     * process
     *
     * @param userName
     * @param userStoreManager
     * @return
     * @throws IdentityException
     */
    public static UserIdentityClaimDTO[] getUserIdentityClaims(String userName,
                                                               UserStoreManager userStoreManager)
            throws IdentityException {
        UserIdentityDataStore store = IdentityMgtConfig.getInstance().getIdentityDataStore();
        UserIdentityClaimsDO userIdentityDO = store.load(userName, userStoreManager);
        if (userIdentityDO != null) {
            return userIdentityDO.getUserIdentityRecoveryData();
        } else {
            throw IdentityException.error("No user account found for user " + userName);
        }
    }

    /**
     * Validates user identity metadata to be valid or invalid.
     *
     * @param userName
     * @param tenantId
     * @param metadataType
     * @param metadata
     * @return
     * @throws IdentityException
     */
    public static boolean isValidIdentityMetadata(String userName, int tenantId, String metadataType,
                                                  String metadata) throws IdentityException {

        return false;
    }

    /**
     * Invalidates the identity metadata
     *
     * @param userName
     * @param tenantId
     * @param metadataType
     * @param metadata
     * @throws IdentityException
     */
    public static void invalidateUserIdentityMetadata(String userName, int tenantId, String metadataType,
                                                      String metadata) throws IdentityException {
        JDBCUserRecoveryDataStore store = new JDBCUserRecoveryDataStore();
        UserRecoveryDataDO metadataDO =
                new UserRecoveryDataDO(userName, tenantId, metadataType,
                        metadata);
        store.invalidate(metadataDO);

    }

    /**
     * Stores new metadata
     *
     * @param metadata
     * @throws IdentityException
     */
    public static void storeUserIdentityMetadata(UserRecoveryDataDO metadata) throws IdentityException {
        JDBCUserRecoveryDataStore store = new JDBCUserRecoveryDataStore();
        metadata.setValid(true);
        store.store(metadata);
    }


    public static void storeUserIdentityClaims(UserIdentityClaimsDO identityClaims,
                                               org.wso2.carbon.user.core.UserStoreManager userStoreManager)
            throws IdentityException {
        IdentityMgtConfig.getInstance().getIdentityDataStore()
                .store(identityClaims, userStoreManager);
    }

    public static UserRecoveryDataDO getUserIdentityMetadata(String userName, int tenantId,
                                                             String metadataType) {
        return null;
    }

    /**
     * Returns all user claims
     *
     * @param userName
     * @return
     * @throws IdentityMgtServiceException
     */
    public static UserIdentityClaimDTO[] getAllUserIdentityClaims(String userName)
            throws IdentityMgtServiceException {
        int tenantId = 0;
        try {
            tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
            UserStoreManager userStoreManager =
                    IdentityMgtServiceComponent.getRealmService()
                            .getTenantUserRealm(tenantId)
                            .getUserStoreManager();
            // read all claims and convert them to UserIdentityClaimDTO
            Claim[] claims = userStoreManager.getUserClaimValues(userName, null);
            List<UserIdentityClaimDTO> allDefaultClaims = new ArrayList<UserIdentityClaimDTO>();
            for (Claim claim : claims) {
                if (claim.getClaimUri().contains(UserCoreConstants.DEFAULT_CARBON_DIALECT)) {
                    UserIdentityClaimDTO claimDTO = new UserIdentityClaimDTO();
                    claimDTO.setClaimUri(claim.getClaimUri());
                    claimDTO.setClaimValue(claim.getValue());
                    allDefaultClaims.add(claimDTO);
                }
            }
            UserIdentityClaimDTO[] claimDTOs = new UserIdentityClaimDTO[allDefaultClaims.size()];
            return allDefaultClaims.toArray(claimDTOs);
        } catch (UserStoreException e) {
            throw new IdentityMgtServiceException("Error while getting user identity claims", e);
        }
    }


    public static void notifyViaEmail(UserIdentityMgtBean bean) {

        //TODO
        // if not module is defined, the default will be loaded

    }

    public static void notifyWithEmail(UserRecoveryDTO notificationBean) {

        // if not module is defined, the default will be loaded

    }

    /**
     * Generates a random password
     *
     * @return
     */
    public static char[] generateTemporaryPassword() {
        IdentityMgtConfig config = IdentityMgtConfig.getInstance();
        return config.getPasswordGenerator().generatePassword();

    }

    /**
     * Returns a random confirmation code
     *
     * @return
     */
    public static String generateRandomConfirmationCode() {
        return new String(generateTemporaryPassword());
    }

    /**
     * @param claims
     * @param tenantId
     * @return
     * @throws IdentityMgtServiceException - If user cannot be retrieved using the provided claims.
     */
    public static String getUsernameByClaims(UserIdentityClaimDTO[] claims, int tenantId)
            throws IdentityMgtServiceException {

        if (claims == null || claims.length < 1) {
            throw new IdentityMgtServiceException("No fields found for user search");
        }

        String userName = null;
        String[] tempUserList = null;

        // Need to populate the claim email as the first element in the
        // passed array.
        for (int i = 0; i < claims.length; i++) {

            UserIdentityClaimDTO claim = claims[i];
            if (claim.getClaimUri() != null && claim.getClaimValue() != null) {

                String[] userList = getUserList(tenantId, claim.getClaimUri(),
                        claim.getClaimValue());

                if (userList != null && userList.length > 0) {
                    if (userList.length == 1) {
                        return userList[0];
                    } else {
                        //If more than one user find the first matching user. Hence need to define unique claims
                        if (tempUserList != null) {
                            for (int j = 0; j < tempUserList.length; j++) {
                                for (int x = 0; x < userList.length; x++) {
                                    if (tempUserList[j].equals(userList[x])) {
                                        return userList[x];
                                    }
                                }
                            }
                        }
                        tempUserList = userList;
                        continue;
                    }
                } else {
                    throw new IdentityMgtServiceException(
                            "No associated user is found for given claim values");

                }
            }
        }

        return userName;
    }

    private static String[] getUserList(int tenantId, String claim, String value) throws IdentityMgtServiceException {

        org.wso2.carbon.user.core.UserStoreManager userStoreManager = null;
        String[] userList = null;
        RealmService realmService = IdentityMgtServiceComponent.getRealmService();

        try {
            if (realmService.getTenantUserRealm(tenantId) != null) {
                userStoreManager = (org.wso2.carbon.user.core.UserStoreManager) realmService.getTenantUserRealm(tenantId).
                        getUserStoreManager();
            }

        } catch (Exception e) {
            String msg = "Error retrieving the user store manager for the tenant";
            throw new IdentityMgtServiceException(msg, e);
        }
        try {
            if (userStoreManager != null) {
                userList = userStoreManager.getUserList(claim, value, null);
            }
            return userList;
        } catch (Exception e) {
            String msg = "Unable to retrieve the claim for the given tenant";
            throw new IdentityMgtServiceException(msg, e);
        }
    }

    public static VerificationBean getCustomErrorMessages(Exception e, String userName) {
        if (e.getMessage().contains(PASSWORD_INVALID)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Old credential does not match with the existing credentials.", e);
            return vBean;
        } else if (e.getMessage().contains(NULL_CREDENTIALS)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Credential is not valid. Credential must be a non null for the user : " + userName, e);
            return vBean;
        }else if (e.getMessage().contains(NULL_USERNAME)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " UserName is not valid. User Name must be a non null", e);
            return vBean;
        }
        else if (e.getMessage().contains(EXISTING_USER)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Username '" + userName
                    + "' already exists in the system. Please pick another username.", e);
            return vBean;
        } else if (e.getMessage().contains(INVALID_CLAIM_URL)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Invalid claim uri has been provided.", e);
            return vBean;
        } else if (e.getMessage().contains(READ_ONLY_STORE)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Read-only UserStoreManager. Roles cannot be added or modified.", e);
            return vBean;
        } else if (e.getMessage().contains(READ_ONLY_PRIMARY_STORE)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Cannot add role to Read Only user store unless it is primary.", e);
            return vBean;
        } else if (e.getMessage().contains(INVALID_ROLE)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Role name not valid. Role name must be a non null string.", e);
            return vBean;
        } else if (e.getMessage().contains(NO_READ_WRITE_PERMISSIONS)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Role cannot be added. User store is read only or cannot write groups.", e);
            return vBean;
        } else if (e.getMessage().contains(EXISTING_ROLE)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Role alreary exists in the system. Please pick another role name.", e);
            return vBean;
        } else if (e.getMessage().contains(SHARED_USER_ROLES)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " User store doesn't support shared user roles functionality.", e);
            return vBean;
        } else if (e.getMessage().contains(REMOVE_ADMIN_USER)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Cannot remove Admin user from Admin role.", e);
            return vBean;
        } else if (e.getMessage().contains(LOGGED_IN_USER)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Cannot remove Admin user from Admin role.", e);
            return vBean;
        } else if (e.getMessage().contains(ADMIN_USER)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Cannot remove Admin user from Admin role.", e);
            return vBean;
        } else if (e.getMessage().contains(ANONYMOUS_USER)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Cannot delete anonymous user.", e);
            return vBean;
        } else if (e.getMessage().contains(INVALID_OPERATION)) {
            vBean = handleError(VerificationBean.ERROR_CODE_UNEXPECTED +
                    " Invalid operation. User store is read only.", e);
            return vBean;
        } else {
            vBean = handleError(
                    VerificationBean.ERROR_CODE_UNEXPECTED + " Error occurred while adding user : " + userName, e);
            return vBean;
        }
    }

    private static VerificationBean handleError(String error, Exception e) {

        VerificationBean bean = new VerificationBean();

        bean.setVerified(false);
        if (error != null) {
            bean.setError(error);
            log.error(error, e);
        } else {
            bean.setError(e.getMessage());
            log.error(e);
        }
        return bean;
    }
}
