package io.bartholomews.scalatestudo.wiremock

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}

case object ResourceFileJsonTransformer extends ResponseDefinitionTransformer {

  import WiremockUtils._

  override val applyGlobally = false

  override def transform(
    request: Request,
    response: ResponseDefinition,
    files: FileSource,
    parameters: Parameters
  ): ResponseDefinition = {

    val requestUrl: String = request.getUrlStripSlashes

    ResponseDefinitionBuilder
      .like(response)
      .but()
      .withBodyFile(s"$requestUrl.json")
      .build()
  }

  override def getName: String = "resource-file-json-transformer"
}
