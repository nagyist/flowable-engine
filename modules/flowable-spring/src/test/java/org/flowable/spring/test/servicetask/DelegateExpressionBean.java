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
package org.flowable.spring.test.servicetask;

import org.flowable.common.engine.impl.el.FixedValue;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joram Barrez
 * @author Bernd Ruecker (camunda)
 */
public class DelegateExpressionBean implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(DelegateExpressionBean.class);
    private SentenceGenerator sentenceGenerator;

    private FixedValue someField;

    @Override
    public void execute(DelegateExecution execution) {
        LOGGER.info("Entering DelegateExpressionBean.execute()");
        if (sentenceGenerator != null) {
            execution.setVariable("myVar", sentenceGenerator.getSentence());
        } else {
            execution.setVariable("myVar", "SentenceGenerator is not injected by spring");
        }
        if (someField != null) {
            execution.setVariable("fieldInjection", someField.getValue(execution));
        } else {
            execution.setVariable("fieldInjection", "Field injection not working");
        }
        LOGGER.info("Leaving DelegateExpressionBean.execute()");
    }

    public void setSentenceGenerator(SentenceGenerator sentenceGenerator) {
        this.sentenceGenerator = sentenceGenerator;
    }

    public FixedValue getSomeField() {
        return someField;
    }

    public void setSomeField(FixedValue someField) {
        this.someField = someField;
    }

}
