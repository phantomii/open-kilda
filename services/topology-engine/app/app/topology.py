from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import db
from app import utils

import sys, os
import requests
import json

@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    try:
        data = {'query' : 'MATCH (n) return n'}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        result_switches = requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        j_switches = json.loads(result_switches.text)
        nodes = []
        topology = {}
        for n in j_switches['data']:
            for r in n:
                node = {}
                node['name'] = (r['data']['name'])
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=auth)
                j_paths = json.loads(result_relationships.text)
                outgoing_relationships = []
                for j_path in j_paths:
                    if j_path['type'] == u'isl':
                        outgoing_relationships.append(j_path['data']['dst_switch'])
                    outgoing_relationships.sort()
                    node['outgoing_relationships'] = outgoing_relationships
            nodes.append(node)
        topology['nodes'] = nodes
        return str(json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


class Nodes(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Edge(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Link(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

@application.route('/api/v1/topology/nodes')
@login_required
def api_v1_topology_nodes():    
    
    nodes = Nodes()
    nodes.edges = []

    i = 0

    while i < 200:
        edge = Edge()
        source = Link()
        target = Link()
        
        source.id = i
        source.label = "s{}".format(str(i))

        target.id = i + 1
        target.label = "s{}".format(str(i + 1))

        edge.value = "link: {}".format(str(i))
        edge.source = source
        edge.target = target


        nodes.edges.append(edge)

        i += 1


    return nodes.toJSON()


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    try:
        data = {'query' : 'MATCH (n) detach delete n'}
        auth = (os.environ['neo4juser'], os.environ['neo4jpass'])
        requests.post(os.environ['neo4jbolt'], data=data, auth=auth)
        return api_v1_network()
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')