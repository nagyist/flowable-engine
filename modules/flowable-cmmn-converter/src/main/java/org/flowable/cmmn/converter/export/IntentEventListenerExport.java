/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.converter.export;

import javax.xml.stream.XMLStreamWriter;

import org.flowable.cmmn.converter.CmmnXmlConstants;
import org.flowable.cmmn.model.IntentEventListener;

public class IntentEventListenerExport extends AbstractPlanItemDefinitionExport<IntentEventListener> {

    @Override
    protected Class<? extends IntentEventListener> getExportablePlanItemDefinitionClass() {
        return IntentEventListener.class;
    }

    @Override
    protected String getPlanItemDefinitionXmlElementValue(IntentEventListener intentEventListener) {
        return ELEMENT_GENERIC_EVENT_LISTENER;
    }

    @Override
    protected void writePlanItemDefinitionSpecificAttributes(IntentEventListener intentEventListener, XMLStreamWriter xtw) throws Exception {
        super.writePlanItemDefinitionSpecificAttributes(intentEventListener, xtw);
        
        xtw.writeAttribute(FLOWABLE_EXTENSIONS_NAMESPACE, CmmnXmlConstants.ATTRIBUTE_EVENT_LISTENER_TYPE, "intent");
    }

}
