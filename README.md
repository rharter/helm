# Helm

Helm is a multiplatform implementation of Cashapp's Broadway architecture based on a
[talk from Droidcon NYC 2022](https://www.droidcon.com/2022/09/29/architecture-at-scale/).

This is an experimental library that's not really intended for public use.

## Usage

Helm is a navigation and presentation framework that allows you to build your UI and Presenter layer
using Jetpack Compose all the way down.

The basic implementation consists of a `Screen`, which is the data that represents a navigation
destination, a `Presenter`, a class containing a single Composable function that takes in a stream
of `UiEvent`s and converts them into `UiModel`s, and a `Ui`, which displays `UiModel`s and emits
`UiEvent`s.

These three basic building blocks allow for decoupled layers that are easy to test and wire
together.

## License

```
Copyright 2022 Ryan Harter

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```