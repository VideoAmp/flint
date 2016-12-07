import React from 'react';
import {ListItem} from 'material-ui/List';
import Trash from 'material-ui/svg-icons/action/delete';

const Instance = () => (
      <ListItem
        primaryText="t2.micro-i-871287b6 123.45.67.89 Master"
        rightIcon={<Trash />}
      />
);

export default Instance;
