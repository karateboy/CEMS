package models

object CdxWebService {

  val service = {
    val ctrl = new com.wecc.cdx.NodeFileCtrl
    ctrl.getNodeFileCtrlSoap
  }
}