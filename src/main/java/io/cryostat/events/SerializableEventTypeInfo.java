/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.events;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

public record SerializableEventTypeInfo(
        String name,
        String typeId,
        String description,
        String[] category,
        Map<String, SerializableOptionDescriptor> options) {

    public static SerializableEventTypeInfo fromEventTypeInfo(IEventTypeInfo info) {
        var name = info.getName();
        var typeId = info.getEventTypeID().getFullKey();
        var description = info.getDescription();
        var category = info.getHierarchicalCategory();

        Map<String, ? extends IOptionDescriptor<?>> origOptions = info.getOptionDescriptors();
        Map<String, SerializableOptionDescriptor> options = new HashMap<>(origOptions.size());
        for (Map.Entry<String, ? extends IOptionDescriptor<?>> entry : origOptions.entrySet()) {
            options.put(
                    entry.getKey(),
                    SerializableOptionDescriptor.fromOptionDescriptor(entry.getValue()));
        }

        return new SerializableEventTypeInfo(name, typeId, description, category, options);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(category);
        result = prime * result + Objects.hash(description, name, options, typeId);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        SerializableEventTypeInfo other = (SerializableEventTypeInfo) obj;
        return Arrays.equals(category, other.category)
                && Objects.equals(description, other.description)
                && Objects.equals(name, other.name)
                && Objects.equals(options, other.options)
                && Objects.equals(typeId, other.typeId);
    }
}
