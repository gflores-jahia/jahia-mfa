/*
 * Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jahia.modules.mfa.otp.graphql.extensions;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.mfa.graphql.extensions.GqlMFAQuery;

/**
 * Admin mutation extension
 */
@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Queries for MFA OTP")
public class MFAOTPQueryExtension {

    /**
     * mfa
     *
     * @return mfa queries
     */
    @GraphQLField
    @GraphQLName("mfaOTP")
    @GraphQLDescription("MFA queries")
    public static GqlMFAOTPQuery mfaOTP() {
        return new GqlMFAOTPQuery();
    }
}
