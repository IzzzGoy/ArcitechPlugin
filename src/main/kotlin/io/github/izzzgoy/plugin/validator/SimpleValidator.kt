package io.github.izzzgoy.plugin.validator

import net.pwall.json.schema.JSONSchema

class SimpleValidator : Validator {

    override fun validate(string: String) {
        JSONSchema.parse(this::class.java.classLoader.getResource("validation_schema.json").file)
            .validate(string)
    }

}