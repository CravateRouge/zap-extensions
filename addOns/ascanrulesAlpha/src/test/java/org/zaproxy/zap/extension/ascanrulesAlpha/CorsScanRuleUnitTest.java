/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.ascanrulesAlpha;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.zaproxy.zap.testutils.NanoServerHandler;

/** Unit test for {@link CorsScanRule}. */
public class CorsScanRuleUnitTest extends ActiveScannerTest<CorsScanRule> {
    private static final String ACAC = "Access-Control-Allow-Credentials";
    private static final String GENERIC_RESPONSE =
            "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n"
                    + "<html><head></head><body></body></html>";

    @Override
    protected CorsScanRule createScanner() {
        return new CorsScanRule();
    }

    @Test
    public void shouldNotAlertIfCORSNotSupported() throws Exception {
        // Given
        nano.addHandler(new CORSResponse(null, false));
        HttpMessage msg = this.getHttpMessage("/");
        rule.init(msg, this.parent);
        // When
        rule.scan();
        // Then
        assertThat(alertsRaised, hasSize(0));
    }

    @Test
    public void shouldAlertInfoIfACAOButNotPayloads() throws Exception {
        // Given
        nano.addHandler(new CORSResponse("dummyValue", false));
        HttpMessage msg = this.getHttpMessage("/");
        rule.init(msg, this.parent);
        // When
        rule.scan();
        // Then
        assertRisk(Alert.RISK_INFO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REFLECT", "*", "null"})
    public void shouldAlertMediumIfACAOPayloads(String origin) throws Exception {
        // Given
        nano.addHandler(new CORSResponse(origin, false));
        HttpMessage msg = this.getHttpMessage("/");
        rule.init(msg, this.parent);
        // When
        rule.scan();
        // Then
        assertRisk(Alert.RISK_MEDIUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REFLECT", "null"})
    public void shouldAlertHighIfACAOAndACACPayloads(String origin) throws Exception {
        // Given
        nano.addHandler(new CORSResponse(origin, true));
        HttpMessage msg = this.getHttpMessage("/");
        rule.init(msg, this.parent);
        // When
        rule.scan();
        // Then
        assertRisk(Alert.RISK_HIGH);
    }

    @Test
    public void shouldAlertMediumIfACAOWildcardAndACAC() throws Exception {
        // Given
        nano.addHandler(new CORSResponse("*", true));
        HttpMessage msg = this.getHttpMessage("/");
        rule.init(msg, this.parent);
        // When
        rule.scan();
        // Then
        assertRisk(Alert.RISK_MEDIUM);
    }

    private void assertRisk(int risk) {
        assertThat(alertsRaised, hasSize(1));
        Alert alert = alertsRaised.get(0);
        assertEquals(risk, alert.getRisk());
    }

    private static class CORSResponse extends NanoServerHandler {
        private final String acaoBehavior;
        private final boolean isAcac;

        public CORSResponse(String acaoBehavior, boolean isAcac) {
            super("/");
            this.acaoBehavior = acaoBehavior;
            this.isAcac = isAcac;
        }

        @Override
        protected Response serve(IHTTPSession session) {
            Response resp = newFixedLengthResponse(GENERIC_RESPONSE);
            if (acaoBehavior == null) {
                return resp;
            }
            String acaoVal = null;
            if (acaoBehavior.equals("REFLECT")) {
                acaoVal = session.getHeaders().get(HttpRequestHeader.ORIGIN);
            } else {
                acaoVal = acaoBehavior;
            }
            resp.addHeader(HttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN, acaoVal);

            if (isAcac) {
                resp.addHeader(ACAC, "true");
            }

            return resp;
        }
    }
}
