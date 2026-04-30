// 省市区数据
const regionData = [
  {
    value: '110000',
    label: '北京市',
    children: [
      {
        value: '110100',
        label: '北京市',
        children: [
          { value: '110101', label: '东城区' },
          { value: '110102', label: '西城区' },
          { value: '110105', label: '朝阳区' },
          { value: '110106', label: '丰台区' },
          { value: '110107', label: '石景山区' },
          { value: '110108', label: '海淀区' },
          { value: '110109', label: '门头沟区' },
          { value: '110111', label: '房山区' },
          { value: '110112', label: '通州区' },
          { value: '110113', label: '顺义区' },
          { value: '110114', label: '昌平区' },
          { value: '110115', label: '大兴区' },
          { value: '110116', label: '怀柔区' },
          { value: '110117', label: '平谷区' },
          { value: '110118', label: '密云区' },
          { value: '110119', label: '延庆区' }
        ]
      }
    ]
  },
  {
    value: '310000',
    label: '上海市',
    children: [
      {
        value: '310100',
        label: '上海市',
        children: [
          { value: '310101', label: '黄浦区' },
          { value: '310104', label: '徐汇区' },
          { value: '310105', label: '长宁区' },
          { value: '310106', label: '静安区' },
          { value: '310107', label: '普陀区' },
          { value: '310109', label: '虹口区' },
          { value: '310110', label: '杨浦区' },
          { value: '310112', label: '闵行区' },
          { value: '310113', label: '宝山区' },
          { value: '310114', label: '嘉定区' },
          { value: '310115', label: '浦东新区' },
          { value: '310116', label: '金山区' },
          { value: '310117', label: '松江区' },
          { value: '310118', label: '青浦区' },
          { value: '310119', label: '奉贤区' },
          { value: '310120', label: '崇明区' }
        ]
      }
    ]
  },
  {
    value: '440000',
    label: '广东省',
    children: [
      {
        value: '440100',
        label: '广州市',
        children: [
          { value: '440103', label: '荔湾区' },
          { value: '440104', label: '越秀区' },
          { value: '440105', label: '海珠区' },
          { value: '440106', label: '天河区' },
          { value: '440111', label: '白云区' },
          { value: '440112', label: '黄埔区' },
          { value: '440113', label: '番禺区' },
          { value: '440114', label: '花都区' },
          { value: '440115', label: '南沙区' },
          { value: '440117', label: '从化区' },
          { value: '440118', label: '增城区' }
        ]
      },
      {
        value: '440300',
        label: '深圳市',
        children: [
          { value: '440303', label: '罗湖区' },
          { value: '440304', label: '福田区' },
          { value: '440305', label: '南山区' },
          { value: '440306', label: '宝安区' },
          { value: '440307', label: '龙岗区' },
          { value: '440308', label: '盐田区' },
          { value: '440309', label: '龙华区' },
          { value: '440310', label: '坪山区' },
          { value: '440311', label: '光明区' },
          { value: '440312', label: '大鹏新区' }
        ]
      }
    ]
  }
];

export default regionData;