/**
 * 
 */
angular.module('realtimeStatusListView', [])
.controller('realtimeStatusListCtrl',
[ '$timeout',
  '$scope',
  '$http',
  function($timeout, $scope, $http){
	var timer;
	var local_url = '/assets/localization/zh_tw.json';
	
	var local_url = '/assets/localization/zh_tw.json';

	function loadContent(){
		$.ajax({
			url : "/RealtimeStatusContent",
			data : "",
			contentType : "application/json; charset=utf-8",
			type : "GET",
			dataType : "html",
			success : function(result) {
				$('#realtimeStatusContent').html(result);				
						
			},
			error : function(xhr, status, errorThrown) {
				console.log("錯誤訊息:" + status + "-" + errorThrown);
			},

			complete : function(xhr, status) {
				$("body").css("cursor", "default");
			}
		});

	}
	
	function reload(){
		loadContent();
		timer = $timeout(reload, 60000);		
	};

	timer = $timeout(reload, 0);
	
    $scope.$on("$destroy", function() {
        if (timer) {
            $timeout.cancel(timer);
        }
    });
}]);