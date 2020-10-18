package org.jahia.modules.mfa.otp.provider;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.jcr.RepositoryException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.io.Charsets;
import org.jahia.modules.mfa.MFAConstants;
import org.jahia.modules.mfa.provider.JahiaMFAProvider;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jboss.aerogear.security.otp.Totp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JahiaMFAOtpProvider extends JahiaMFAProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JahiaMFAOtpProvider.class);
    private static final String ALGORITHM = "AES";
    private static final String KEY = "jahia-mfa-otp-provider";
    private static final int KEY_SIZE = 32;
    private static final int TOTP_KEY_BYTE_SIZE = 20;

    public JahiaMFAOtpProvider() {
        super(KEY);
    }

    @Override
    public boolean verifyToken(JCRUserNode userNode, String token, String password) {

        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
                    final JCRNodeWrapper defaultUserNode = jcrsession.getNode(userNode.getPath());
                    final JCRNodeWrapper mfaNode = defaultUserNode.getNode(MFAConstants.NODE_NAME_MFA);
                    final String encryptedSecretKey = mfaNode.getPropertyAsString(Constants.PROP_SECRET_KEY);
                    final Totp totp = new Totp(decryptTotpSecretKey(encryptedSecretKey, password));
                    return isValidLong(token) && totp.verify(token);
                }
            });
        } catch (RepositoryException ex) {
            LOGGER.error(String.format("Impossible to vertify OTP code for user %s", userNode.getUserKey()), ex);
            return false;
        }
    }

    @Override
    public boolean activateMFA(JCRUserNode userNode, String password) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                @Override
                public Boolean doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
                    final JCRNodeWrapper defaultUserNode = jcrsession.getNode(userNode.getPath());
                    final JCRNodeWrapper mfaNode = defaultUserNode.getNode(MFAConstants.NODE_NAME_MFA);
                    mfaNode.addMixin(Constants.MIXIN_MFA_OTP);
                    mfaNode.setProperty(Constants.PROP_SECRET_KEY, encryptTotpSecretKey(generateTotpSecret(), password));
                    jcrsession.save();
                    return true;
                }
            });
        } catch (RepositoryException | IllegalStateException ex) {
            LOGGER.error(String.format("Impossible to activate MFA OTP for user %s", userNode.getUserKey()), ex);
            return false;
        }
    }

    @Override
    public boolean deactivateMFA(JCRUserNode userNode) {
        try {
            if (isActivated(userNode)) {
                return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
                    @Override
                    public Boolean doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
                        final JCRNodeWrapper defaultUserNode = jcrsession.getNode(userNode.getPath());
                        final JCRNodeWrapper mfaNode = defaultUserNode.getNode(MFAConstants.NODE_NAME_MFA);
                        if (mfaNode.hasProperty(Constants.PROP_SECRET_KEY)) {
                            mfaNode.getProperty(Constants.PROP_SECRET_KEY).remove();
                        }
                        if (mfaNode.isNodeType(Constants.MIXIN_MFA_OTP)) {
                            mfaNode.removeMixin(Constants.MIXIN_MFA_OTP);
                        }
                        jcrsession.save();
                        return true;
                    }
                });
            }
        } catch (RepositoryException ex) {
            LOGGER.error(String.format("Impossible to deactivate MFA OTP for user %s", userNode.getUserKey()), ex);
        }
        return false;
    }

    public boolean isActivated(JCRUserNode userNode) throws RepositoryException {
        if (userNode.hasNode(MFAConstants.NODE_NAME_MFA)) {
            final JCRNodeWrapper mfaNode = userNode.getNode(MFAConstants.NODE_NAME_MFA);
            return mfaNode.isNodeType(Constants.MIXIN_MFA_OTP);
        }
        return false;
    }

    public void deactivateMFA(AddedNodeFact addedNodeFact) {
        try {
            final JCRUserNode userNode = JahiaUserManagerService.getInstance().lookupUserByPath(addedNodeFact.getPath());
            if (isActivated(userNode)) {
                getJahiaMFAService().deactivateMFA(userNode, KEY);
            }
        } catch (RepositoryException ex) {
            LOGGER.error("Impossible to deactivate MFA OTP", ex);
        }
    }

    private static String encryptTotpSecretKey(String secretKey, String password) {
        try {
            final Key key = generateSecretKey(password);
            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            final byte[] encValue = cipher.doFinal(secretKey.getBytes(Charsets.UTF_8));
            return Base64.getEncoder().encodeToString(encValue);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible to encrypt secret key", ex);
        }
    }

    public static String decryptTotpSecretKey(String encryptedSecretKey, String password) {
        try {

            final Key key = generateSecretKey(password);
            final Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            final byte[] decValue = cipher.doFinal(Base64.getDecoder().decode(encryptedSecretKey));
            return new String(decValue, Charsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible to decrypt secret key", ex);
        }
    }

    private static Key generateSecretKey(String password) {
        return new SecretKeySpec(generateKey(password).getBytes(Charsets.UTF_8), ALGORITHM);
    }

    private static String generateKey(String password) {
        String aesKey = "";
        while (aesKey.length() < KEY_SIZE) {
            aesKey = aesKey + password;
        }
        aesKey = aesKey.substring(0, KEY_SIZE);
        return aesKey;
    }

    private static String generateTotpSecret() {
        final SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[TOTP_KEY_BYTE_SIZE];
        random.nextBytes(bytes);
        final Base32 base32 = new Base32();
        return base32.encodeToString(bytes);
    }

    private boolean isValidLong(String token) {
        try {
            Long.parseLong(token);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }
}
