/**
 * 
 */
angular
		.module(
				'historyTrendView',
				[ 'ui.bootstrap.datetimepicker', 'ui.dateTimeInput',
						'smart-table' ])
		.factory('MonitorTypeService', [ '$resource', function($resource) {
			return $resource('/MonitorType/:id', {
				id : '@_id'
			});
		} ])
		.factory('MonitorService', [ '$resource', function($resource) {
			return $resource('/Monitor/:id', {
				id : '@_id'
			});
		} ])
		.factory('IndParkService', [ '$resource', function($resource) {
			return $resource('/IndPark/:id', {
				id : '@_id'
			});
		} ])
		.factory('ReportUnitService', [ '$resource', function($resource) {
			return $resource('/ReportUnit/:id', {
				id : '@_id'
			});
		} ])		
		.controller(
				'historyTrendCtrl',
				[
						'MonitorTypeService',
						'MonitorService',
						'IndParkService',
						'ReportUnitService',
						'$scope',
						'$http',
						function($monitorType, $monitor, $indPark, $reportUnit, $scope, $http) {
							var self = this;
							self.monitorTypeList = $monitorType.query();
							self.monitorList = $monitor.query();
							self.indParkList = $indPark.query();
							self.reportUnitList = $reportUnit.query();

							self.dateRangeStart = moment(23, "HH").subtract(2,
									'days');
							self.dateRangeEnd = moment(23, "HH");

							self.beforeRenderStartDate = function($view,
									$dates, $leftDate, $upDate, $rightDate) {
								if (self.dateRangeEnd) {
									var activeDate = moment(self.dateRangeEnd);
									for (var i = 0; i < $dates.length; i++) {
										if ($dates[i].localDateValue() >= activeDate
												.valueOf())
											$dates[i].selectable = false;
									}
								}
							}

							self.beforeRenderEndDate = function($view, $dates,
									$leftDate, $upDate, $rightDate) {
								if ($scope.dateRangeStart) {
									var activeDate = moment(self.dateRangeStart)
											.subtract(1, $view)
											.add(1, 'minute');
									for (var i = 0; i < $dates.length; i++) {
										if ($dates[i].localDateValue() <= activeDate
												.valueOf()) {
											$dates[i].selectable = false;
										}
									}
								}
							}
							
							function getSelectedArrayFunction(obj){
								return function(){
									var ret = [];
									for (var prop in obj) {
										if(obj[prop])
											ret.push(prop);
									}
									return ret;
								}	
							}
							
							$scope.checkedIndPark={};
							self.selectedIndPark=getSelectedArrayFunction($scope.checkedIndPark);
							$scope.checkedMonitorId={};
							self.selectedMonitorId=getSelectedArrayFunction($scope.checkedMonitorId);
							$scope.checkedMtId={};
							self.selectedMtId = getSelectedArrayFunction($scope.checkedMtId);
							self.chartTypeList = [
							    {id:"line", name:"折線圖"},
							    {id:"spline", name:"曲線圖"},
							    {id:"area", name:"面積圖"},
							    {id:"areaspline", name:"曲線面積圖"},
							    {id:"column", name:"柱狀圖"},
							    {id:"scatter", name:"點圖"}
							];
							$scope.selectedChartType;
							
							
							self.indParkMonitor = function(m) {
								return (self.selectedIndPark().indexOf(m.indParkName)!=-1)
							}

							self.displayResult = true;
							self.query = function() {
								var base_url = encodeURIComponent(self.selectedMonitorId().join(':')) + 
									"/" + encodeURIComponent(self.selectedMtId().join(':')) + 
									"/" + $scope.selectedReportUnit +  
									"/" + self.dateRangeStart.valueOf() + 
									"/" + self.dateRangeEnd.valueOf();
								
								var url = "/JSON/HistoryTrend/" + base_url; 
									+  

								$http.get(url)
									.then(
										function(response) {
											var result = response.data;
											self.displayResult = true;
											result.chart = {
															type: $scope.selectedChartType,
															zoomType: 'x',
												            panning: true,
												            panKey: 'shift',
												            alignTicks: false,
												            height: 500
														};
											
											var pointFormatter = function(){
												var d = new Date(this.x);
												return d.toLocaleString() + ": " + Math.round(this.y) + "度";
											}
			
											result.colors=[
												'#7CB5EC','#434348','#90ED7D','#F7A35C','#8085E9','#F15C80',
												'#E4D354','#2B908F','#FB9FA8','#91E8E1','#7CB5EC','#80C535','#969696'];
											result.tooltip ={valueDecimals: 2};
											result.legend={enabled:true};
											result.credits={
													enabled:false,
													href:'http://www.wecc.com.tw/'
											};
											result.xAxis.type = "datetime";
											result.xAxis.dateTimeLabelFormats = {
													day: '%b%e日',
											        week: '%b%e日',
											        month: '%y年%b',
											};
														
											result.plotOptions= {
													scatter:{
														tooltip:{
															pointFormatter: pointFormatter
																}
															}
											};

																			
														
											$("#downloadExcel").prop("href", "/Excel/HistoryTrend/" + base_url);
											$('#reportDiv').highcharts(result);
										}, function(error) {});
							}													
						} ]);
