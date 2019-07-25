@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS", "EXTERNAL_DELEGATION", "NESTED_CLASS_IN_EXTERNAL_INTERFACE")

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.MouseEvent
import kotlin.js.Json

external interface `T$0` {
    var global: Chart.ChartOptions /* Chart.ChartOptions & Chart.ChartFontOptions */
    @nativeGetter
    operator fun get(key: String): Any?
    @nativeSetter
    operator fun set(key: String, value: Any)
}
open external class Chart {
    constructor(context: String, options: Chart.ChartConfiguration)
    constructor(context: CanvasRenderingContext2D, options: Chart.ChartConfiguration)
    constructor(context: HTMLCanvasElement, options: Chart.ChartConfiguration)
    constructor(context: Array<dynamic /* CanvasRenderingContext2D | HTMLCanvasElement */>, options: Chart.ChartConfiguration)
    open var config: Chart.ChartConfiguration = definedExternally
    open var data: Chart.ChartData = definedExternally
    open var destroy: () -> Any = definedExternally
    open var update: () -> Any = definedExternally
    open var stop: () -> Any = definedExternally
    open var resize: () -> Any = definedExternally
    open var clear: () -> Any = definedExternally
    open var toBase64Image: () -> String = definedExternally
    open var generateLegend: () -> Any = definedExternally
    open var getElementAtEvent: (e: Any) -> dynamic /* JsTuple<Any?> */ = definedExternally
    open var getElementsAtEvent: (e: Any) -> Array<Any> = definedExternally
    open var getDatasetAtEvent: (e: Any) -> Array<Any> = definedExternally
    open var getDatasetMeta: (index: Number) -> Meta = definedExternally
    open var ctx: CanvasRenderingContext2D? = definedExternally
    open var canvas: HTMLCanvasElement? = definedExternally
    open var width: Number? = definedExternally
    open var height: Number? = definedExternally
    open var aspectRatio: Number? = definedExternally
    open var options: Chart.ChartOptions = definedExternally
    open var chartArea: Chart.ChartArea = definedExternally
    companion object {
        var Chart: Any? = definedExternally
        var defaults: `T$0` = definedExternally
        var controllers: Json = definedExternally
        var helpers: Json = definedExternally
    }
    interface ChartArea {
        var top: Number
        var right: Number
        var bottom: Number
        var left: Number
    }
    interface ChartLegendItem {
        var text: String? get() = definedExternally; set(value) = definedExternally
        var fillStyle: String? get() = definedExternally; set(value) = definedExternally
        var hidden: Boolean? get() = definedExternally; set(value) = definedExternally
        var lineCap: dynamic /* String /* "butt" */ | String /* "round" */ | String /* "square" */ */ get() = definedExternally; set(value) = definedExternally
        var lineDash: Array<Number>? get() = definedExternally; set(value) = definedExternally
        var lineDashOffset: Number? get() = definedExternally; set(value) = definedExternally
        var lineJoin: dynamic /* String /* "round" */ | String /* "bevel" */ | String /* "miter" */ */ get() = definedExternally; set(value) = definedExternally
        var lineWidth: Number? get() = definedExternally; set(value) = definedExternally
        var strokeStyle: String? get() = definedExternally; set(value) = definedExternally
        var pointStyle: dynamic /* String /* "line" */ | String /* "circle" */ | String /* "cross" */ | String /* "crossRot" */ | String /* "dash" */ | String /* "rect" */ | String /* "rectRounded" */ | String /* "rectRot" */ | String /* "star" */ | String /* "triangle" */ */ get() = definedExternally; set(value) = definedExternally
    }
    interface ChartLegendLabelItem : ChartLegendItem {
        var datasetIndex: Number
    }
    interface ChartTooltipItem {
        var label: String? get() = definedExternally; set(value) = definedExternally
        var value: String? get() = definedExternally; set(value) = definedExternally
        var xLabel: dynamic /* String | Number */ get() = definedExternally; set(value) = definedExternally
        var yLabel: dynamic /* String | Number */ get() = definedExternally; set(value) = definedExternally
        var datasetIndex: Number? get() = definedExternally; set(value) = definedExternally
        var index: Number? get() = definedExternally; set(value) = definedExternally
        var x: Number? get() = definedExternally; set(value) = definedExternally
        var y: Number? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartTooltipLabelColor {
        var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */
        var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */
    }
    interface ChartTooltipCallback {
        val beforeTitle: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val title: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val afterTitle: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val beforeBody: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val beforeLabel: ((tooltipItem: ChartTooltipItem, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val label: ((tooltipItem: ChartTooltipItem, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val labelColor: ((tooltipItem: ChartTooltipItem, chart: Chart) -> ChartTooltipLabelColor)? get() = definedExternally
        val labelTextColor: ((tooltipItem: ChartTooltipItem, chart: Chart) -> String)? get() = definedExternally
        val afterLabel: ((tooltipItem: ChartTooltipItem, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val afterBody: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val beforeFooter: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val footer: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
        val afterFooter: ((item: Array<ChartTooltipItem>, data: ChartData) -> dynamic /* String | Array<String> */)? get() = definedExternally
    }
    interface ChartAnimationParameter {
        var chartInstance: Any? get() = definedExternally; set(value) = definedExternally
        var animationObject: Any? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartPoint {
        var x: dynamic /* String | Number | Date */ get() = definedExternally; set(value) = definedExternally
        var y: dynamic /* String | Number | Date */ get() = definedExternally; set(value) = definedExternally
        var r: Number? get() = definedExternally; set(value) = definedExternally
        var t: dynamic /* String | Number | Date */ get() = definedExternally; set(value) = definedExternally
    }
    interface ChartConfiguration {
        var type: dynamic /* String /* "line" */ | String /* "bar" */ | String /* "horizontalBar" */ | String /* "radar" */ | String /* "doughnut" */ | String /* "polarArea" */ | String /* "bubble" */ | String /* "pie" */ | String /* "scatter" */ | String */ get() = definedExternally; set(value) = definedExternally
        var data: ChartData? get() = definedExternally; set(value) = definedExternally
        var options: ChartOptions? get() = definedExternally; set(value) = definedExternally
        var plugins: Array<PluginServiceRegistrationOptions>? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartData {
        var labels: Array<dynamic /* String | Array<String> */>? get() = definedExternally; set(value) = definedExternally
        var datasets: Array<ChartDataSets>? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartSize {
        var height: Number
        var width: Number
    }
    interface ChartOptions {
        var responsive: Boolean? get() = definedExternally; set(value) = definedExternally
        var responsiveAnimationDuration: Number? get() = definedExternally; set(value) = definedExternally
        var aspectRatio: Number? get() = definedExternally; set(value) = definedExternally
        var maintainAspectRatio: Boolean? get() = definedExternally; set(value) = definedExternally
        var events: Array<String>? get() = definedExternally; set(value) = definedExternally
        val legendCallback: ((chart: Chart) -> String)? get() = definedExternally
        val onHover: ((`this`: Chart, event: MouseEvent, activeElements: Array<Any>) -> Any)? get() = definedExternally
        val onClick: ((event: MouseEvent? /*= null*/, activeElements: Array<Any>? /*= null*/) -> Any)? get() = definedExternally
        val onResize: ((`this`: Chart, newSize: ChartSize) -> Unit)? get() = definedExternally
        var title: ChartTitleOptions? get() = definedExternally; set(value) = definedExternally
        var legend: ChartLegendOptions? get() = definedExternally; set(value) = definedExternally
        var tooltips: ChartTooltipOptions? get() = definedExternally; set(value) = definedExternally
        var hover: ChartHoverOptions? get() = definedExternally; set(value) = definedExternally
        var animation: ChartAnimationOptions? get() = definedExternally; set(value) = definedExternally
        var elements: ChartElementsOptions? get() = definedExternally; set(value) = definedExternally
        var layout: ChartLayoutOptions? get() = definedExternally; set(value) = definedExternally
        var scales: ChartScales? get() = definedExternally; set(value) = definedExternally
        var showLines: Boolean? get() = definedExternally; set(value) = definedExternally
        var spanGaps: Boolean? get() = definedExternally; set(value) = definedExternally
        var cutoutPercentage: Number? get() = definedExternally; set(value) = definedExternally
        var circumference: Number? get() = definedExternally; set(value) = definedExternally
        var rotation: Number? get() = definedExternally; set(value) = definedExternally
        var devicePixelRatio: Number? get() = definedExternally; set(value) = definedExternally
        var plugins: ChartPluginsOptions? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartFontOptions {
        var defaultFontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var defaultFontFamily: String? get() = definedExternally; set(value) = definedExternally
        var defaultFontSize: Number? get() = definedExternally; set(value) = definedExternally
        var defaultFontStyle: String? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartTitleOptions {
        var display: Boolean? get() = definedExternally; set(value) = definedExternally
        var position: dynamic /* String /* "left" */ | String /* "right" */ | String /* "top" */ | String /* "bottom" */ */ get() = definedExternally; set(value) = definedExternally
        var fullWidth: Boolean? get() = definedExternally; set(value) = definedExternally
        var fontSize: Number? get() = definedExternally; set(value) = definedExternally
        var fontFamily: String? get() = definedExternally; set(value) = definedExternally
        var fontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var fontStyle: String? get() = definedExternally; set(value) = definedExternally
        var padding: Number? get() = definedExternally; set(value) = definedExternally
        var text: dynamic /* String | Array<String> */ get() = definedExternally; set(value) = definedExternally
    }
    interface ChartLegendOptions {
        var display: Boolean? get() = definedExternally; set(value) = definedExternally
        var position: dynamic /* String /* "left" */ | String /* "right" */ | String /* "top" */ | String /* "bottom" */ */ get() = definedExternally; set(value) = definedExternally
        var fullWidth: Boolean? get() = definedExternally; set(value) = definedExternally
        val onClick: ((event: MouseEvent, legendItem: ChartLegendLabelItem) -> Unit)? get() = definedExternally
        val onHover: ((event: MouseEvent, legendItem: ChartLegendLabelItem) -> Unit)? get() = definedExternally
        var labels: ChartLegendLabelOptions? get() = definedExternally; set(value) = definedExternally
        var reverse: Boolean? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartLegendLabelOptions {
        var boxWidth: Number? get() = definedExternally; set(value) = definedExternally
        var fontSize: Number? get() = definedExternally; set(value) = definedExternally
        var fontStyle: String? get() = definedExternally; set(value) = definedExternally
        var fontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var fontFamily: String? get() = definedExternally; set(value) = definedExternally
        var padding: Number? get() = definedExternally; set(value) = definedExternally
        val generateLabels: ((chart: Chart) -> Array<ChartLegendLabelItem>)? get() = definedExternally
        val filter: ((legendItem: ChartLegendLabelItem, data: ChartData) -> Any)? get() = definedExternally
        var usePointStyle: Boolean? get() = definedExternally; set(value) = definedExternally
    }
    interface ChartTooltipOptions {
        var enabled: Boolean? get() = definedExternally; set(value) = definedExternally
        val custom: ((a: Any) -> Unit)? get() = definedExternally
        var mode: dynamic /* String /* "point" */ | String /* "nearest" */ | String /* "single" */ | String /* "label" */ | String /* "index" */ | String /* "x-axis" */ | String /* "dataset" */ | String /* "x" */ | String /* "y" */ */ get() = definedExternally; set(value) = definedExternally
        var intersect: Boolean? get() = definedExternally; set(value) = definedExternally
        var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var titleFontFamily: String? get() = definedExternally; set(value) = definedExternally
        var titleFontSize: Number? get() = definedExternally; set(value) = definedExternally
        var titleFontStyle: String? get() = definedExternally; set(value) = definedExternally
        var titleFontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var titleSpacing: Number? get() = definedExternally; set(value) = definedExternally
        var titleMarginBottom: Number? get() = definedExternally; set(value) = definedExternally
        var bodyFontFamily: String? get() = definedExternally; set(value) = definedExternally
        var bodyFontSize: Number? get() = definedExternally; set(value) = definedExternally
        var bodyFontStyle: String? get() = definedExternally; set(value) = definedExternally
        var bodyFontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var bodySpacing: Number? get() = definedExternally; set(value) = definedExternally
        var footerFontFamily: String? get() = definedExternally; set(value) = definedExternally
        var footerFontSize: Number? get() = definedExternally; set(value) = definedExternally
        var footerFontStyle: String? get() = definedExternally; set(value) = definedExternally
        var footerFontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var footerSpacing: Number? get() = definedExternally; set(value) = definedExternally
        var footerMarginTop: Number? get() = definedExternally; set(value) = definedExternally
        var xPadding: Number? get() = definedExternally; set(value) = definedExternally
        var yPadding: Number? get() = definedExternally; set(value) = definedExternally
        var caretSize: Number? get() = definedExternally; set(value) = definedExternally
        var cornerRadius: Number? get() = definedExternally; set(value) = definedExternally
        var multiKeyBackground: String? get() = definedExternally; set(value) = definedExternally
        var callbacks: ChartTooltipCallback? get() = definedExternally; set(value) = definedExternally
        val filter: ((item: ChartTooltipItem, data: ChartData) -> Boolean)? get() = definedExternally
        val itemSort: ((itemA: ChartTooltipItem, itemB: ChartTooltipItem) -> Number)? get() = definedExternally
        var position: String? get() = definedExternally; set(value) = definedExternally
        var caretPadding: Number? get() = definedExternally; set(value) = definedExternally
        var displayColors: Boolean? get() = definedExternally; set(value) = definedExternally
        var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
        var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
    }
}
external interface Meta {
    var type: dynamic /* String /* "line" */ | String /* "bar" */ | String /* "horizontalBar" */ | String /* "radar" */ | String /* "doughnut" */ | String /* "polarArea" */ | String /* "bubble" */ | String /* "pie" */ | String /* "scatter" */ */
    var data: Array<MetaData>
    var controller: Json
    var hidden: Boolean? get() = definedExternally; set(value) = definedExternally
    var total: String? get() = definedExternally; set(value) = definedExternally
    var xAxisID: String? get() = definedExternally; set(value) = definedExternally
    var yAxisID: String? get() = definedExternally; set(value) = definedExternally
}
external interface MetaData {
    var _chart: Chart
    var _datasetIndex: Number
    var _index: Number
    var _model: Model
    var _start: Any? get() = definedExternally; set(value) = definedExternally
    var _view: Model
    var hidden: Boolean? get() = definedExternally; set(value) = definedExternally
}
external interface Model {
    var backgroundColor: String
    var borderColor: String
    var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
    var controlPointNextX: Number
    var controlPointNextY: Number
    var controlPointPreviousX: Number
    var controlPointPreviousY: Number
    var hitRadius: Number
    var pointStyle: String
    var radius: String
    var skip: Boolean? get() = definedExternally; set(value) = definedExternally
    var steppedLine: Nothing? get() = definedExternally; set(value) = definedExternally
    var tension: Number
    var x: Number
    var y: Number
    var base: Number
    var head: Number
}
external interface ChartPluginsOptions {
    @nativeGetter
    operator fun get(pluginId: String): Any?
    @nativeSetter
    operator fun set(pluginId: String, value: Any)
}
external interface `T$1` {
    @nativeGetter
    operator fun get(mode: String): ((elements: Array<Any>, eventPosition: Point) -> Point)?
    @nativeSetter
    operator fun set(mode: String, value: (elements: Array<Any>, eventPosition: Point) -> Point)
}
external interface ChartTooltipsStaticConfiguration {
    var positioners: `T$1`
}
external interface ChartHoverOptions {
    var animationDuration: Number? get() = definedExternally; set(value) = definedExternally
    var intersect: Boolean? get() = definedExternally; set(value) = definedExternally
    val onHover: ((`this`: Chart, event: MouseEvent, activeElements: Array<Any>) -> Any)? get() = definedExternally
}
external interface ChartAnimationObject {
    var currentStep: Number? get() = definedExternally; set(value) = definedExternally
    var numSteps: Number? get() = definedExternally; set(value) = definedExternally
    val render: ((arg: Any) -> Unit)? get() = definedExternally
    val onAnimationProgress: ((arg: Any) -> Unit)? get() = definedExternally
    val onAnimationComplete: ((arg: Any) -> Unit)? get() = definedExternally
}
external interface ChartAnimationOptions {
    var duration: Number? get() = definedExternally; set(value) = definedExternally
    val onProgress: ((chart: Any) -> Unit)? get() = definedExternally
    val onComplete: ((chart: Any) -> Unit)? get() = definedExternally
    var animateRotate: Boolean? get() = definedExternally; set(value) = definedExternally
    var animateScale: Boolean? get() = definedExternally; set(value) = definedExternally
}
external interface ChartElementsOptions {
    var point: ChartPointOptions? get() = definedExternally; set(value) = definedExternally
    var line: ChartLineOptions? get() = definedExternally; set(value) = definedExternally
    var arc: ChartArcOptions? get() = definedExternally; set(value) = definedExternally
    var rectangle: ChartRectangleOptions? get() = definedExternally; set(value) = definedExternally
}
external interface ChartArcOptions {
    var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
}
external interface ChartLineOptions {
    var cubicInterpolationMode: dynamic /* String /* "default" */ | String /* "monotone" */ */ get() = definedExternally; set(value) = definedExternally
    var tension: Number? get() = definedExternally; set(value) = definedExternally
    var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
    var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderCapStyle: String? get() = definedExternally; set(value) = definedExternally
    var borderDash: Array<Any>? get() = definedExternally; set(value) = definedExternally
    var borderDashOffset: Number? get() = definedExternally; set(value) = definedExternally
    var borderJoinStyle: String? get() = definedExternally; set(value) = definedExternally
    var capBezierPoints: Boolean? get() = definedExternally; set(value) = definedExternally
    var fill: dynamic /* Boolean | String /* "top" */ | String /* "bottom" */ | String /* "zero" */ */ get() = definedExternally; set(value) = definedExternally
    var stepped: Boolean? get() = definedExternally; set(value) = definedExternally
}
external interface ChartPointOptions {
    var radius: Number? get() = definedExternally; set(value) = definedExternally
    var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
    var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var hitRadius: Number? get() = definedExternally; set(value) = definedExternally
    var hoverRadius: Number? get() = definedExternally; set(value) = definedExternally
    var hoverBorderWidth: Number? get() = definedExternally; set(value) = definedExternally
}
external interface ChartRectangleOptions {
    var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderWidth: Number? get() = definedExternally; set(value) = definedExternally
    var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderSkipped: String? get() = definedExternally; set(value) = definedExternally
}
external interface ChartLayoutOptions {
    var padding: dynamic /* Number | ChartLayoutPaddingObject */ get() = definedExternally; set(value) = definedExternally
}
external interface ChartLayoutPaddingObject {
    var top: Number? get() = definedExternally; set(value) = definedExternally
    var right: Number? get() = definedExternally; set(value) = definedExternally
    var bottom: Number? get() = definedExternally; set(value) = definedExternally
    var left: Number? get() = definedExternally; set(value) = definedExternally
}
external interface GridLineOptions {
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var color: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var borderDash: Array<Number>? get() = definedExternally; set(value) = definedExternally
    var borderDashOffset: Number? get() = definedExternally; set(value) = definedExternally
    var lineWidth: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var drawBorder: Boolean? get() = definedExternally; set(value) = definedExternally
    var drawOnChartArea: Boolean? get() = definedExternally; set(value) = definedExternally
    var drawTicks: Boolean? get() = definedExternally; set(value) = definedExternally
    var tickMarkLength: Number? get() = definedExternally; set(value) = definedExternally
    var zeroLineWidth: Number? get() = definedExternally; set(value) = definedExternally
    var zeroLineColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var zeroLineBorderDash: Array<Number>? get() = definedExternally; set(value) = definedExternally
    var zeroLineBorderDashOffset: Number? get() = definedExternally; set(value) = definedExternally
    var offsetGridLines: Boolean? get() = definedExternally; set(value) = definedExternally
}
external interface ScaleTitleOptions {
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var labelString: String? get() = definedExternally; set(value) = definedExternally
    var lineHeight: dynamic /* String | Number */ get() = definedExternally; set(value) = definedExternally
    var fontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var fontFamily: String? get() = definedExternally; set(value) = definedExternally
    var fontSize: Number? get() = definedExternally; set(value) = definedExternally
    var fontStyle: String? get() = definedExternally; set(value) = definedExternally
    var padding: dynamic /* Number | ChartLayoutPaddingObject */ get() = definedExternally; set(value) = definedExternally
}
external interface TickOptions : NestedTickOptions {
    var minor: dynamic /* Boolean | NestedTickOptions */ get() = definedExternally; set(value) = definedExternally
    var major: dynamic /* Boolean | NestedTickOptions */ get() = definedExternally; set(value) = definedExternally
}
external interface NestedTickOptions {
    var autoSkip: Boolean? get() = definedExternally; set(value) = definedExternally
    var autoSkipPadding: Number? get() = definedExternally; set(value) = definedExternally
    var backdropColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var backdropPaddingX: Number? get() = definedExternally; set(value) = definedExternally
    var backdropPaddingY: Number? get() = definedExternally; set(value) = definedExternally
    var beginAtZero: Boolean? get() = definedExternally; set(value) = definedExternally
    val callback: ((value: Any, index: Any, values: Any) -> dynamic /* String | Number */)? get() = definedExternally
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var fontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var fontFamily: String? get() = definedExternally; set(value) = definedExternally
    var fontSize: Number? get() = definedExternally; set(value) = definedExternally
    var fontStyle: String? get() = definedExternally; set(value) = definedExternally
    var labelOffset: Number? get() = definedExternally; set(value) = definedExternally
    var lineHeight: Number? get() = definedExternally; set(value) = definedExternally
    var max: Any? get() = definedExternally; set(value) = definedExternally
    var maxRotation: Number? get() = definedExternally; set(value) = definedExternally
    var maxTicksLimit: Number? get() = definedExternally; set(value) = definedExternally
    var min: Any? get() = definedExternally; set(value) = definedExternally
    var minRotation: Number? get() = definedExternally; set(value) = definedExternally
    var mirror: Boolean? get() = definedExternally; set(value) = definedExternally
    var padding: Number? get() = definedExternally; set(value) = definedExternally
    var reverse: Boolean? get() = definedExternally; set(value) = definedExternally
    var showLabelBackdrop: Boolean? get() = definedExternally; set(value) = definedExternally
    var source: dynamic /* String /* "auto" */ | String /* "data" */ | String /* "labels" */ */ get() = definedExternally; set(value) = definedExternally
    var stepSize: Number? get() = definedExternally; set(value) = definedExternally
    var suggestedMax: Number? get() = definedExternally; set(value) = definedExternally
    var suggestedMin: Number? get() = definedExternally; set(value) = definedExternally
}
external interface AngleLineOptions {
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var color: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var lineWidth: Number? get() = definedExternally; set(value) = definedExternally
}
external interface PointLabelOptions {
    val callback: ((arg: Any) -> Any)? get() = definedExternally
    var fontColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */ get() = definedExternally; set(value) = definedExternally
    var fontFamily: String? get() = definedExternally; set(value) = definedExternally
    var fontSize: Number? get() = definedExternally; set(value) = definedExternally
    var fontStyle: String? get() = definedExternally; set(value) = definedExternally
}
external interface LinearTickOptions : TickOptions {
    override var maxTicksLimit: Number? get() = definedExternally; set(value) = definedExternally
    override var stepSize: Number? get() = definedExternally; set(value) = definedExternally
    var precision: Number? get() = definedExternally; set(value) = definedExternally
    override var suggestedMin: Number? get() = definedExternally; set(value) = definedExternally
    override var suggestedMax: Number? get() = definedExternally; set(value) = definedExternally
}
external interface LogarithmicTickOptions : TickOptions
external interface ChartDataSets {
    var cubicInterpolationMode: dynamic /* String /* "default" */ | String /* "monotone" */ */ get() = definedExternally; set(value) = definedExternally
    var backgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var borderWidth: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var borderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var borderCapStyle: dynamic /* String /* "butt" */ | String /* "round" */ | String /* "square" */ */ get() = definedExternally; set(value) = definedExternally
    var borderDash: Array<Number>? get() = definedExternally; set(value) = definedExternally
    var borderDashOffset: Number? get() = definedExternally; set(value) = definedExternally
    var borderJoinStyle: dynamic /* String /* "round" */ | String /* "bevel" */ | String /* "miter" */ */ get() = definedExternally; set(value) = definedExternally
    var data: dynamic /* Array<Number> | Array<Any> */ get() = definedExternally; set(value) = definedExternally
    var fill: dynamic /* String | Number | Boolean */ get() = definedExternally; set(value) = definedExternally
    var hoverBackgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var hoverBorderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var hoverBorderWidth: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var label: String? get() = definedExternally; set(value) = definedExternally
    var lineTension: Number? get() = definedExternally; set(value) = definedExternally
    var steppedLine: dynamic /* Boolean | String /* "before" */ | String /* "after" */ | String /* "middle" */ */ get() = definedExternally; set(value) = definedExternally
    var pointBorderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var pointBackgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var pointBorderWidth: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var pointRadius: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var pointHoverRadius: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var pointHitRadius: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var pointHoverBackgroundColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var pointHoverBorderColor: dynamic /* String | CanvasGradient | CanvasPattern | Array<String> | Array<dynamic /* String | CanvasGradient | CanvasPattern | Array<String> */> */ get() = definedExternally; set(value) = definedExternally
    var pointHoverBorderWidth: dynamic /* Number | Array<Number> */ get() = definedExternally; set(value) = definedExternally
    var pointStyle: dynamic /* PointStyle | HTMLImageElement | HTMLCanvasElement | Array<dynamic /* PointStyle | HTMLImageElement | HTMLCanvasElement */> */ get() = definedExternally; set(value) = definedExternally
    var xAxisID: String? get() = definedExternally; set(value) = definedExternally
    var yAxisID: String? get() = definedExternally; set(value) = definedExternally
    var type: dynamic /* ChartType | String */ get() = definedExternally; set(value) = definedExternally
    var hidden: Boolean? get() = definedExternally; set(value) = definedExternally
    var hideInLegendAndTooltip: Boolean? get() = definedExternally; set(value) = definedExternally
    var showLine: Boolean? get() = definedExternally; set(value) = definedExternally
    var stack: String? get() = definedExternally; set(value) = definedExternally
    var spanGaps: Boolean? get() = definedExternally; set(value) = definedExternally
}
external interface ChartScales {
    var type: dynamic /* ScaleType | String */ get() = definedExternally; set(value) = definedExternally
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var position: dynamic /* PositionType | String */ get() = definedExternally; set(value) = definedExternally
    var gridLines: GridLineOptions? get() = definedExternally; set(value) = definedExternally
    var scaleLabel: ScaleTitleOptions? get() = definedExternally; set(value) = definedExternally
    var ticks: TickOptions? get() = definedExternally; set(value) = definedExternally
    var xAxes: Array<ChartXAxe>? get() = definedExternally; set(value) = definedExternally
    var yAxes: Array<ChartYAxe>? get() = definedExternally; set(value) = definedExternally
}
external interface CommonAxe {
    var bounds: String? get() = definedExternally; set(value) = definedExternally
    var type: dynamic /* ScaleType | String */ get() = definedExternally; set(value) = definedExternally
    var display: Boolean? get() = definedExternally; set(value) = definedExternally
    var id: String? get() = definedExternally; set(value) = definedExternally
    var stacked: Boolean? get() = definedExternally; set(value) = definedExternally
    var position: String? get() = definedExternally; set(value) = definedExternally
    var ticks: TickOptions? get() = definedExternally; set(value) = definedExternally
    var gridLines: GridLineOptions? get() = definedExternally; set(value) = definedExternally
    var barThickness: dynamic /* Number | String /* "flex" */ */ get() = definedExternally; set(value) = definedExternally
    var maxBarThickness: Number? get() = definedExternally; set(value) = definedExternally
    var scaleLabel: ScaleTitleOptions? get() = definedExternally; set(value) = definedExternally
    var time: TimeScale? get() = definedExternally; set(value) = definedExternally
    var offset: Boolean? get() = definedExternally; set(value) = definedExternally
    val beforeUpdate: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeSetDimension: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeDataLimits: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeBuildTicks: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeTickToLabelConversion: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeCalculateTickRotation: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeFit: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterUpdate: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterSetDimension: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterDataLimits: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterBuildTicks: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterTickToLabelConversion: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterCalculateTickRotation: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterFit: ((scale: Any? /*= null*/) -> Unit)? get() = definedExternally
}
external interface ChartXAxe : CommonAxe {
    var categoryPercentage: Number? get() = definedExternally; set(value) = definedExternally
    var barPercentage: Number? get() = definedExternally; set(value) = definedExternally
    var distribution: dynamic /* String /* "linear" */ | String /* "series" */ */ get() = definedExternally; set(value) = definedExternally
}
external interface ChartYAxe : CommonAxe
external interface TimeDisplayFormat {
    var millisecond: String? get() = definedExternally; set(value) = definedExternally
    var second: String? get() = definedExternally; set(value) = definedExternally
    var minute: String? get() = definedExternally; set(value) = definedExternally
    var hour: String? get() = definedExternally; set(value) = definedExternally
    var day: String? get() = definedExternally; set(value) = definedExternally
    var week: String? get() = definedExternally; set(value) = definedExternally
    var month: String? get() = definedExternally; set(value) = definedExternally
    var quarter: String? get() = definedExternally; set(value) = definedExternally
    var year: String? get() = definedExternally; set(value) = definedExternally
}
external interface TimeScale : ChartScales {
    var displayFormats: TimeDisplayFormat? get() = definedExternally; set(value) = definedExternally
    var isoWeekday: Boolean? get() = definedExternally; set(value) = definedExternally
    var max: String? get() = definedExternally; set(value) = definedExternally
    var min: String? get() = definedExternally; set(value) = definedExternally
    var parser: dynamic /* String | (arg: Any) -> Any */ get() = definedExternally; set(value) = definedExternally
    var tooltipFormat: String? get() = definedExternally; set(value) = definedExternally
    var unitStepSize: Number? get() = definedExternally; set(value) = definedExternally
    var stepSize: Number? get() = definedExternally; set(value) = definedExternally
}
external interface Point {
    var x: Number
    var y: Number
}
external interface PluginServiceGlobalRegistration {
    var id: String? get() = definedExternally; set(value) = definedExternally
}
external interface PluginServiceRegistrationOptions {
    val beforeInit: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterInit: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeLayout: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterLayout: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeDatasetsUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterDatasetsUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val beforeDatasetUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    val afterDatasetUpdate: ((chartInstance: Chart, options: Any? /*= null*/) -> Unit)? get() = definedExternally
    var once: Any
}
