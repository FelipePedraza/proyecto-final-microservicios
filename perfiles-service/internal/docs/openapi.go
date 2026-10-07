package docs

import (
	_ "embed"
)

//go:embed openapi.json
var OpenAPIV3 []byte
