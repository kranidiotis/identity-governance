/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.captcha.validator;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.common.model.User;
import org.wso2.carbon.identity.captcha.exception.CaptchaException;
import org.wso2.carbon.identity.captcha.util.CaptchaConstants;
import org.wso2.carbon.identity.captcha.util.CaptchaUtil;
import org.wso2.carbon.identity.core.bean.context.MessageContext;
import org.wso2.carbon.identity.core.handler.AbstractIdentityMessageHandler;
import org.wso2.carbon.identity.core.model.IdentityEventListenerConfig;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants.EventName;
import org.wso2.carbon.identity.event.IdentityEventConstants.EventProperty;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.util.Map;

/**
 * FailLoginAttemptValidator.
 */
public class FailLoginAttemptValidator extends AbstractEventHandler {

    private static Log log = LogFactory.getLog(FailLoginAttemptValidator.class);

    @Override
    public String getName() {
        return "failLoginAttemptValidator";
    }


    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        boolean isEnabled = isFailLoginAttemptValidatorEnabled(event);

        if (!isEnabled) {
            return;
        }

        if (event.getEventProperties().get(EventProperty.CONTEXT) instanceof AuthenticationContext &&
                event.getEventProperties().get(EventProperty.PARAMS) instanceof Map) {
            AuthenticationContext context = (AuthenticationContext) event.getEventProperties().get(EventProperty.CONTEXT);
            Map<String, Object> unmodifiableParamMap = (Map<String, Object>) event.getEventProperties()
                    .get(EventProperty.PARAMS);
            String eventName = event.getEventName();

            if (EventName.AUTHENTICATION_STEP_FAILURE.name().equals(eventName)) {
                publishAuthenticationStepFailure(context, unmodifiableParamMap);
                if (log.isDebugEnabled() && event != null) {
                    log.debug(this.getName() + " received event : " + event.getEventName());
                }
            }
        }

    }

    protected void publishAuthenticationStepFailure(AuthenticationContext authenticationContext, Map<String, Object> map) {

        String currentAuthenticator = authenticationContext.getCurrentAuthenticator();
        if (StringUtils.isBlank(currentAuthenticator) && MapUtils.isNotEmpty(map)) {
            currentAuthenticator = (String) map.get(FrameworkConstants.AUTHENTICATOR);
        }
        if ("BasicAuthenticator".equals(currentAuthenticator) && map != null && map.get
                (FrameworkConstants.AnalyticsAttributes.USER) != null) {

            if (map.get(FrameworkConstants.AnalyticsAttributes.USER) instanceof User) {
                User failedUser = (User) map.get(FrameworkConstants.AnalyticsAttributes.USER);
                String username = failedUser.getUserName();
                if (!StringUtils.isBlank(failedUser.getUserStoreDomain()) &&
                        !IdentityUtil.getPrimaryDomainName().equals(failedUser.getUserStoreDomain())) {
                    username = UserCoreUtil.addDomainToName(username, failedUser.getUserStoreDomain());
                }
                try {
                    if (CaptchaUtil.isMaximumFailedLoginAttemptsReached(username, failedUser.getTenantDomain())) {
                        CaptchaConstants.setEnableSecurityMechanism("enable");
                    }
                } catch (CaptchaException e) {
                    log.error("Failed to evaluate max failed attempts of the user.", e);
                }
            }
        }
    }

    @Override
    public boolean isEnabled(MessageContext messageContext) {
        IdentityEventListenerConfig identityEventListenerConfig = IdentityUtil.readEventListenerProperty
                (AbstractIdentityMessageHandler.class.getName(), this.getClass().getName());

        if (identityEventListenerConfig == null) {
            return false;
        }

        return Boolean.parseBoolean(identityEventListenerConfig.getEnable());
    }

    private boolean isFailLoginAttemptValidatorEnabled(Event event) throws IdentityEventException {

        boolean isEnabled = false;

        String handlerEnabled = this.configs.getModuleProperties().getProperty(CaptchaConstants.
                FAIL_LOGIN_ATTEMPT_VALIDATOR_ENABLED);
        isEnabled = Boolean.parseBoolean(handlerEnabled);

        return isEnabled;
    }
}
